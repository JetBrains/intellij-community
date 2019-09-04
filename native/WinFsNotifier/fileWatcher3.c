// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <ctype.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <Windows.h>

enum RootState {
	rsOff,
	rsRunning,
	rsFailed
};

typedef struct __WatchRoot {
    char rootPath[MAX_PATH + 1];
    HANDLE hThread;
    HANDLE hStopEvent;
	bool bUsed;
	enum RootState state;
	struct __WatchRoot *next;
} WatchRoot;

static WatchRoot *firstWatchRoot = NULL;

static CRITICAL_SECTION csOutput;

#define EVENT_BUFFER_SIZE (16*1024)

#ifdef __PRINT_STATS
static UINT64 _total_ = 0, _post_ = 0;
static UINT32 _calls_ = 0, _max_events_ = 0;
#endif

// -- Utilities ---------------------------------------------------

#define IS_SET(flags, flag) (((flags) & (flag)) == (flag))
#define FILE_SHARE_ALL (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

typedef DWORD (WINAPI *GetFinalPathNameByHandlePtr)(HANDLE, LPCWSTR, DWORD, DWORD);
static GetFinalPathNameByHandlePtr pGetFinalPathNameByHandle = NULL;

typedef struct {
    char *text;
    size_t size;
} PrintBuffer;

static void AppendString(PrintBuffer *buffer, const char *str) {
    if (buffer->text == NULL || strlen(buffer->text) + strlen(str) + 1 > buffer->size) {
        size_t newSize = buffer->size + max(4096, strlen(str));
        char *newData = (char *)malloc(newSize);
        if (buffer->text != NULL) {
            strcpy_s(newData, newSize, buffer->text);
            free(buffer->text);
        } else {
            newData[0] = '\0';
        }
        buffer->text = newData;
        buffer->size = newSize;
    }
    strcat_s(buffer->text, buffer->size, str);
}

// -- Volume operations ---------------------------------------------------

static bool IsDriveWatchable(const char *rootPath) {
    UINT type = GetDriveTypeA(rootPath);
    if (type == DRIVE_REMOVABLE || type == DRIVE_FIXED || type == DRIVE_RAMDISK) {
        char fsName[MAX_PATH + 1];
        if (GetVolumeInformationA(rootPath, NULL, 0, NULL, NULL, NULL, fsName, sizeof(fsName))) {
            return strcmp(fsName, "NTFS") == 0 ||
                   strcmp(fsName, "FAT") == 0 ||
                   strcmp(fsName, "FAT32") == 0 ||
                   _stricmp(fsName, "exFAT") == 0 ||
                   _stricmp(fsName, "reFS") == 0;
        }
    }

    return false;
}

static bool IsPathWatchable(const char *pathToWatch) {
    bool watchable = true;
    int pathLen = MultiByteToWideChar(CP_UTF8, 0, pathToWatch, -1, NULL, 0);
    wchar_t *path = (wchar_t *)calloc((size_t)pathLen + 1, sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, pathToWatch, -1, path, pathLen);
    wchar_t buffer[1024];
    const unsigned int bufferSize = 1024;

    wchar_t *pSlash;
    while ((pSlash = wcsrchr(path, L'\\')) != NULL) {
        DWORD attributes = GetFileAttributesW(path);
        if (attributes != INVALID_FILE_ATTRIBUTES && IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
            if (pGetFinalPathNameByHandle != NULL) {
                HANDLE h = CreateFileW(path, 0, FILE_SHARE_ALL, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
                if (h != INVALID_HANDLE_VALUE) {
                    DWORD result = pGetFinalPathNameByHandle(h, buffer, bufferSize, 0);
                    CloseHandle(h);
                    if (result > 0 && result < bufferSize && wcsncmp(buffer, L"\\\\?\\UNC\\", 8) == 0) {
                        watchable = false;
                        break;
                    }
                }
            }

            path[pathLen - 1] = L'\\';
            path[pathLen] = L'\0';
        }

        *pSlash = L'\0';
        pathLen = (int)(pSlash - path);
    }

    free(path);
    return watchable;
}

static void PrintUnwatchablePaths(PrintBuffer *buffer, UINT32 unwatchable) {
    for (WatchRoot *root = firstWatchRoot; root; root = root->next) {
        const char *path = root->rootPath;
        if (!IsPathWatchable(path)) {
            AppendString(buffer, path);
            AppendString(buffer, "\n");
        }
    }
}

// -- Watcher thread ----------------------------------------------------------

static void PrintChangeInfo(const char *rootPath, FILE_NOTIFY_INFORMATION *info) {
    const char *event;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "CREATE";
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "DELETE";
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        event = "CHANGE";
    } else {
        return;  // unknown event
    }

    char utfBuffer[4 * MAX_PATH + 1];
    int wcsLen = (int)(info->FileNameLength / sizeof(wchar_t));
    int converted = WideCharToMultiByte(CP_UTF8, 0, info->FileName, wcsLen, utfBuffer, sizeof(utfBuffer), NULL, NULL);
    utfBuffer[converted] = '\0';

    EnterCriticalSection(&csOutput);
    puts(event);
    printf(rootPath);
    puts(utfBuffer);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

static void PrintEverythingChangedUnderRoot(const char *rootPath) {
    EnterCriticalSection(&csOutput);
    puts("RECDIRTY");
    puts(rootPath);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

#define CREATE_SHARE (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)
#define CREATE_FLAGS (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED)
#define EVENT_MASK (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | \
                    FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE | FILE_NOTIFY_CHANGE_LAST_WRITE)

static DWORD WINAPI WatcherThread(void *param) {
#ifdef __PRINT_STATS
    LARGE_INTEGER t1, t2, t3;
    UINT32 nEvents = 0;
#endif
    WatchRoot *pRoot = (WatchRoot *)param;

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    const char *rootPath = pRoot->rootPath;
    HANDLE hRootDir = CreateFileA(rootPath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

    char buffer[EVENT_BUFFER_SIZE];
    HANDLE handles[2] = {pRoot->hStopEvent, overlapped.hEvent};
    while (true) {
        int rcDir = ReadDirectoryChangesW(hRootDir, buffer, sizeof(buffer), TRUE, EVENT_MASK, NULL, &overlapped, NULL);
        if (rcDir == 0) {
            pRoot->state = rsFailed;
            break;
        }

        DWORD rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
        if (rc == WAIT_OBJECT_0) {
            break;
        }
        if (rc == WAIT_OBJECT_0 + 1) {
#ifdef __PRINT_STATS
            QueryPerformanceCounter(&t1);
#endif
            DWORD dwBytesReturned;
            if (!GetOverlappedResult(hRootDir, &overlapped, &dwBytesReturned, FALSE)) {
                pRoot->state = rsFailed;
                break;
            }

#ifdef __PRINT_STATS
            QueryPerformanceCounter(&t2);
#endif
            if (dwBytesReturned == 0) {
                // don't send dirty too much, everything is changed anyway
                if (WaitForSingleObject(pRoot->hStopEvent, 500) == WAIT_OBJECT_0)
                    break;

                // Got a buffer overflow => current changes lost => send RECDIRTY on root
                PrintEverythingChangedUnderRoot(rootPath);
            } else {
                FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *)buffer;
                do {
                    PrintChangeInfo(rootPath, info);
                    info = (FILE_NOTIFY_INFORMATION *)((char *)info + info->NextEntryOffset);
#ifdef __PRINT_STATS
                    nEvents++;
#endif
                } while (info->NextEntryOffset != 0);
            }

#ifdef __PRINT_STATS
            QueryPerformanceCounter(&t3);
            _post_ += t2.QuadPart - t1.QuadPart;
            _total_ += t3.QuadPart - t1.QuadPart;
            _calls_++;
            _max_events_ = max(_max_events_, nEvents);
#endif
        }
    }

    CloseHandle(overlapped.hEvent);
    CloseHandle(hRootDir);
    return 0;
}

// -- Roots update ------------------------------------------------------------

static void MarkAllRootsUnused() {
	WatchRoot *pRoot = firstWatchRoot;
	while (pRoot) {
        pRoot->bUsed = false;
		pRoot = pRoot->next;
    }
}

static void StartRoot(WatchRoot *root) {
    root->hStopEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    root->hThread = CreateThread(NULL, 0, &WatcherThread, root, 0, NULL);
    SetThreadPriority(root->hThread, THREAD_PRIORITY_ABOVE_NORMAL);
    root->state = rsRunning;
}

static void StopRoot(WatchRoot *root) {
    SetEvent(root->hStopEvent);
    WaitForSingleObject(root->hThread, INFINITE);
    CloseHandle(root->hThread);
    CloseHandle(root->hStopEvent);
    root->state = rsOff;
}

static void UpdateRoots(bool report) {
    UINT32 unwatchable = 0;

	WatchRoot *pRoot = firstWatchRoot;
	while (pRoot) {
		if (!pRoot->bUsed || pRoot->state == rsFailed) {
			StopRoot(pRoot);
		}

		if (pRoot->bUsed && pRoot->state == rsOff) {
			StartRoot(pRoot);
		}

		pRoot = pRoot->next;
	}

    if (!report) {
        return;
    }

    PrintBuffer buffer = {NULL, 0};
    AppendString(&buffer, "UNWATCHEABLE\n");
    PrintUnwatchablePaths(&buffer, unwatchable);
    AppendString(&buffer, "#\nREMAP\n");
    AppendString(&buffer, "#");

    EnterCriticalSection(&csOutput);
    puts(buffer.text);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);

    free(buffer.text);
}

static void AddWatchRoot(const char *path) {
    WatchRoot *root = (WatchRoot *)malloc(sizeof(WatchRoot));
    root->next = NULL;
	memset(root->rootPath, 0, sizeof(root->rootPath));
	strncpy(root->rootPath, path, MAX_PATH);
	if (root->rootPath[strlen(root->rootPath)-1] != '\\') {
		strcat(root->rootPath, "\\");
	}
	root->hThread = NULL;
	root->hStopEvent = NULL;
	root->state = rsOff;
    root->next = firstWatchRoot;
    firstWatchRoot = root;
}

static void FreeWatchRootsList() {
    WatchRoot *root = firstWatchRoot, *next;
    while (root) {
        next = root->next;
        free(root);
        root = next;
    }
    firstWatchRoot = NULL;
}

// -- Main - file watcher protocol ---------------------------------------------

int main(int argc, char *argv[]) {
    SetErrorMode(SEM_FAILCRITICALERRORS);
    pGetFinalPathNameByHandle = (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "GetFinalPathNameByHandleW");
    InitializeCriticalSection(&csOutput);

    char buffer[8192];
    while (true) {
        if (!gets_s(buffer, sizeof(buffer) - 1) || strcmp(buffer, "EXIT") == 0) {
            break;
        }

        if (strcmp(buffer, "ROOTS") == 0) {
            MarkAllRootsUnused();
			UpdateRoots(false);
            FreeWatchRootsList();

            bool failed = false;
            while (true) {
                if (!gets_s(buffer, sizeof(buffer) - 1)) {
                    failed = true;
                    break;
                }
                if (strlen(buffer) == 0) {
                    continue;
                }
                if (buffer[0] == '#') {
                    break;
                }

                char *root = buffer;
                if (*root == '|') root++;
                AddWatchRoot(root);
            }
            if (failed) {
                break;
            }

            UpdateRoots(true);
        }
    }

    MarkAllRootsUnused();
    UpdateRoots(false);
    DeleteCriticalSection(&csOutput);

#ifdef __PRINT_STATS
    if (_calls_ > 0) {
        LARGE_INTEGER fcy;
        QueryPerformanceFrequency(&fcy);
        _total_ = _total_ * 1000000 / fcy.QuadPart;
        _post_ = _post_ * 1000000 / fcy.QuadPart;
        fprintf(stderr, "!! TOTAL=%llu(%d) POST=%llu(%d) MAX.EVENTS=%u\n",
                _total_, (int) (_total_ / _calls_),
                _post_, (int) (_post_ / _calls_),
                _max_events_);
    }
#endif

    return 0;
}

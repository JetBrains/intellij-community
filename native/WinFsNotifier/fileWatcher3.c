// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <ctype.h>
#include <fcntl.h>
#include <io.h>
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
    wchar_t rootPath[MAX_PATH + 1];
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
    wchar_t *text;
    size_t size;
} PrintBuffer;

static void AppendString(PrintBuffer *buffer, const wchar_t *str) {
    if (buffer->text == NULL || wcslen(buffer->text) + wcslen(str) + 1 > buffer->size) {
        size_t newSize = buffer->size + max(4096, wcslen(str));
        wchar_t *newData = (wchar_t *)calloc(newSize, sizeof(wchar_t));
        if (buffer->text != NULL) {
            wcscpy_s(newData, newSize, buffer->text);
            free(buffer->text);
        } else {
            newData[0] = L'\0';
        }
        buffer->text = newData;
        buffer->size = newSize;
    }
    wcscat_s(buffer->text, buffer->size, str);
}

// -- Volume operations ---------------------------------------------------

static bool IsDriveWatchable(const wchar_t *rootPath) {
    UINT type = GetDriveTypeW(rootPath);
    if (type == DRIVE_REMOVABLE || type == DRIVE_FIXED || type == DRIVE_RAMDISK) {
        wchar_t fsName[MAX_PATH + 1];
        if (GetVolumeInformationW(rootPath, NULL, 0, NULL, NULL, NULL, fsName, sizeof(fsName)/sizeof(wchar_t))) {
            return wcscmp(fsName, L"NTFS") == 0 ||
                   wcscmp(fsName, L"FAT") == 0 ||
                   wcscmp(fsName, L"FAT32") == 0 ||
                   _wcsicmp(fsName, L"exFAT") == 0 ||
                   _wcsicmp(fsName, L"reFS") == 0;
        }
    }

    return false;
}

static bool IsPathWatchable(const wchar_t *pathToWatch) {
    bool watchable = true;
	wchar_t *path = _wcsdup(pathToWatch);
    wchar_t buffer[1024];
    const unsigned int bufferSize = 1024;
	size_t pathLen = wcslen(path);

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
        const wchar_t *path = root->rootPath;
        if (!IsPathWatchable(path)) {
            AppendString(buffer, path);
            AppendString(buffer, L"\n");
        }
    }
}

// -- Watcher thread ----------------------------------------------------------

static void PrintChangeInfo(const wchar_t *rootPath, FILE_NOTIFY_INFORMATION *info) {
    const wchar_t *event;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = L"CREATE";
    } else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = L"DELETE";
    } else if (info->Action == FILE_ACTION_MODIFIED) {
        event = L"CHANGE";
    } else {
        return;  // unknown event
    }

	wchar_t *filename = (wchar_t*)calloc(info->FileNameLength + 1, sizeof(wchar_t));
	memcpy(filename, info->FileName, info->FileNameLength);

    EnterCriticalSection(&csOutput);
    _putws(event);
    wprintf(rootPath);
    _putws(filename);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);

	free(filename);
}

static void PrintEverythingChangedUnderRoot(const wchar_t *rootPath) {
    EnterCriticalSection(&csOutput);
    _putws(L"RECDIRTY");
    _putws(rootPath);
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

    const wchar_t *rootPath = pRoot->rootPath;
    HANDLE hRootDir = CreateFileW(rootPath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

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
    AppendString(&buffer, L"UNWATCHEABLE\n");
    PrintUnwatchablePaths(&buffer, unwatchable);
    AppendString(&buffer, L"#\nREMAP\n");
    AppendString(&buffer, L"#");

    EnterCriticalSection(&csOutput);
    _putws(buffer.text);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);

    free(buffer.text);
}

static bool GetClosestExistingDirectory(const wchar_t *path)
{
	struct _stat buffer;
	bool result = false;
	while (true) {
		int res = _wstat(path, &buffer);
		if (res == 0 && (buffer.st_mode & _S_IFDIR) == _S_IFDIR) {
			result = true;
			break;
		}
		wchar_t *p = wcsrchr(path, L'\\');
		if (p != NULL) {
			*p = 0;
			continue;
		}
		break;
	}

	return result;
}

static void AddWatchRoot(const wchar_t *path) {
    WatchRoot *root = (WatchRoot *)calloc(1, sizeof(WatchRoot));
    root->next = NULL;
	wcsncpy(root->rootPath, path, MAX_PATH);
	if (GetClosestExistingDirectory(root->rootPath)) {
		if (root->rootPath[wcslen(root->rootPath)-1] != L'\\') {
			wcscat(root->rootPath, L"\\");
		}
		root->hThread = NULL;
		root->hStopEvent = NULL;
		root->state = rsOff;
		root->bUsed = true;
		root->next = firstWatchRoot;
		firstWatchRoot = root;
	} else {
		free(root);
	}
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
	_setmode(_fileno(stdin), _O_U8TEXT);
	_setmode(_fileno(stdout), _O_U8TEXT);
    pGetFinalPathNameByHandle = (GetFinalPathNameByHandlePtr)GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "GetFinalPathNameByHandleW");
    InitializeCriticalSection(&csOutput);

    wchar_t buffer[8192];
	int bufferSize = sizeof(buffer) / sizeof(wchar_t);
    while (true) {
        if (!_getws_s(buffer, bufferSize - 1) || wcscmp(buffer, L"EXIT") == 0) {
            break;
        }
        if (wcscmp(buffer, L"ROOTS") == 0) {
            MarkAllRootsUnused();
			UpdateRoots(false);
            FreeWatchRootsList();

            bool failed = false;
            while (true) {
                if (!_getws_s(buffer, bufferSize - 1)) {
                    failed = true;
                    break;
                }
                if (wcslen(buffer) == 0) {
                    continue;
                }
                if (buffer[0] == L'#') {
                    break;
                }

                wchar_t *root = buffer;
                if (*root == L'|') root++;
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
        fwprintf(stderr, L"!! TOTAL=%llu(%d) POST=%llu(%d) MAX.EVENTS=%u\n",
                _total_, (int) (_total_ / _calls_),
                _post_, (int) (_post_ / _calls_),
                _max_events_);
    }
#endif

    return 0;
}

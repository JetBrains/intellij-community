// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <ctype.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <windows.h>

typedef struct {
    char rootPath[4];
    HANDLE hThread;
    HANDLE hStopEvent;
    bool bInitialized;
    bool bUsed;
    bool bFailed;
} WatchRootInfo;

#define ROOT_COUNT ('Z'-'A'+1)
static WatchRootInfo watchRootInfo[ROOT_COUNT];

typedef struct __WatchRoot {
    char *path;
    struct __WatchRoot *next;
} WatchRoot;

static WatchRoot *firstWatchRoot = NULL;

static CRITICAL_SECTION csOutput;

// -- Utilities ---------------------------------------------------

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
        }
        else {
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
    wchar_t *path = (wchar_t *)calloc((size_t)pathLen, sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, pathToWatch, -1, path, pathLen);

    while (wcschr(path, L'\\') != NULL) {
        DWORD attributes = GetFileAttributesW(path);
        if (attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
            watchable = false;
            break;
        }
        wchar_t *pSlash = wcsrchr(path, L'\\');
        if (pSlash != NULL) {
            *pSlash = L'\0';
        }
    }

    free(path);
    return watchable;
}

static void PrintUnwatcheableDrives(PrintBuffer *buffer, UINT32 unwatchable) {
    for (int i = 0; i < ROOT_COUNT; i++) {
        if ((unwatchable & (1 << i)) != 0) {
            AppendString(buffer, watchRootInfo[i].rootPath);
            AppendString(buffer, "\n");
        }
    }
}

static void PrintUnwatcheablePaths(PrintBuffer *buffer, UINT32 unwatchable) {
    for (WatchRoot *pWatchRoot = firstWatchRoot; pWatchRoot; pWatchRoot = pWatchRoot->next) {
        const char *path = pWatchRoot->path;
        int drive = path[0] - 'A';
        if ((unwatchable & (1 << drive)) == 0 && !IsPathWatchable(path)) {
            AppendString(buffer, path);
            AppendString(buffer, "\n");
        }
    }
}

static void PrintRemapForSubstDrives(PrintBuffer *buffer) {
    wchar_t targetPath[MAX_PATH];
    char targetPathUtf[4 * MAX_PATH];
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchRootInfo[i].bUsed) {
            wchar_t device[3] = {btowc(watchRootInfo[i].rootPath[0]), L':', L'\0'};
            DWORD res = QueryDosDeviceW(device, targetPath, sizeof(targetPath) / sizeof(wchar_t));
            if (res > 4 && targetPath[0] == L'\\' && targetPath[1] == L'?' && targetPath[2] == L'?' && targetPath[3] == L'\\') {
                WideCharToMultiByte(CP_UTF8, 0, targetPath + 4, -1, targetPathUtf, sizeof(targetPathUtf), NULL, NULL);
                AppendString(buffer, watchRootInfo[i].rootPath);
                AppendString(buffer, "\n");
                AppendString(buffer, targetPathUtf);
                AppendString(buffer, "\n");
            }
        }
    }
}

// -- Watcher thread ----------------------------------------------------------

static void PrintChangeInfo(const char *rootPath, FILE_NOTIFY_INFORMATION *info) {
    const char *event;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "CREATE";
    }
    else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        event = "DELETE";
    }
    else if (info->Action == FILE_ACTION_MODIFIED) {
        event = "CHANGE";
    }
    else {
        return;  // unknown event
    }

    char utfBuffer[4 * MAX_PATH + 1];
    int converted = WideCharToMultiByte(CP_UTF8, 0, info->FileName, info->FileNameLength / sizeof(wchar_t), utfBuffer, sizeof(utfBuffer), NULL, NULL);
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
    WatchRootInfo *rootInfo = (WatchRootInfo *)param;

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    const char *rootPath = rootInfo->rootPath;
    HANDLE hRootDir = CreateFileA(rootPath, GENERIC_READ, CREATE_SHARE, NULL, OPEN_EXISTING, CREATE_FLAGS, NULL);

    DWORD buffer_size = 1024 * 1024;
    char *buffer = (char *)malloc(buffer_size);

    HANDLE handles[2];
    handles[0] = rootInfo->hStopEvent;
    handles[1] = overlapped.hEvent;
    while (true) {
        int rcDir = ReadDirectoryChangesW(hRootDir, buffer, buffer_size, TRUE, EVENT_MASK, NULL, &overlapped, NULL);
        if (rcDir == 0) {
            rootInfo->bFailed = true;
            break;
        }

        DWORD rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
        if (rc == WAIT_OBJECT_0) {
            break;
        }
        if (rc == WAIT_OBJECT_0 + 1) {
            DWORD dwBytesReturned;
            if (!GetOverlappedResult(hRootDir, &overlapped, &dwBytesReturned, FALSE)) {
                rootInfo->bFailed = true;
                break;
            }

            if (dwBytesReturned == 0) {
                // don't send dirty too much, everything is changed anyway
                if (WaitForSingleObject(rootInfo->hStopEvent, 500) == WAIT_OBJECT_0)
                    break;

                // Got a buffer overflow => current changes lost => send RECDIRTY on root
                PrintEverythingChangedUnderRoot(rootPath);
            } else {
                FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *) buffer;
                while (true) {
                    PrintChangeInfo(rootPath, info);
                    if (!info->NextEntryOffset)
                        break;
                    info = (FILE_NOTIFY_INFORMATION *)((char *) info + info->NextEntryOffset);
                }
            }
        }
    }
    CloseHandle(overlapped.hEvent);
    CloseHandle(hRootDir);
    free(buffer);
    return 0;
}

// -- Roots update ------------------------------------------------------------

static void MarkAllRootsUnused() {
    for (int i = 0; i < ROOT_COUNT; i++) {
        watchRootInfo[i].bUsed = false;
    }
}

static void StartRoot(WatchRootInfo *info) {
    info->hStopEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    info->hThread = CreateThread(NULL, 0, &WatcherThread, info, 0, NULL);
    info->bInitialized = true;
}

static void StopRoot(WatchRootInfo *info) {
    SetEvent(info->hStopEvent);
    WaitForSingleObject(info->hThread, INFINITE);
    CloseHandle(info->hThread);
    CloseHandle(info->hStopEvent);
    info->bInitialized = false;
}

static void UpdateRoots(bool report) {
    UINT32 unwatchable = 0;
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchRootInfo[i].bInitialized && (!watchRootInfo[i].bUsed || watchRootInfo[i].bFailed)) {
            StopRoot(&watchRootInfo[i]);
            watchRootInfo[i].bFailed = false;
        }
        if (watchRootInfo[i].bUsed) {
            if (!IsDriveWatchable(watchRootInfo[i].rootPath)) {
                unwatchable |= (1 << i);
                watchRootInfo[i].bUsed = false;
                continue;
            }
            if (!watchRootInfo[i].bInitialized) {
                StartRoot(&watchRootInfo[i]);
            }
        }
    }

    if (!report) {
        return;
    }

    PrintBuffer buffer = {NULL, 0};
    AppendString(&buffer, "UNWATCHEABLE\n");
    PrintUnwatcheableDrives(&buffer, unwatchable);
    PrintUnwatcheablePaths(&buffer, unwatchable);
    AppendString(&buffer, "#\nREMAP\n");
    PrintRemapForSubstDrives(&buffer);
    AppendString(&buffer, "#");

    EnterCriticalSection(&csOutput);
    puts(buffer.text);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);

    free(buffer.text);
}

static void AddWatchRoot(const char *path) {
    WatchRoot *watchRoot = (WatchRoot *)malloc(sizeof(WatchRoot));
    watchRoot->next = NULL;
    watchRoot->path = _strdup(path);
    watchRoot->next = firstWatchRoot;
    firstWatchRoot = watchRoot;
}

static void FreeWatchRootsList() {
    WatchRoot *pWatchRoot = firstWatchRoot, *pNext;
    while (pWatchRoot) {
        pNext = pWatchRoot->next;
        free(pWatchRoot->path);
        free(pWatchRoot);
        pWatchRoot = pNext;
    }
    firstWatchRoot = NULL;
}

// -- Main - file watcher protocol ---------------------------------------------

int main(int argc, char *argv[]) {
    SetErrorMode(SEM_FAILCRITICALERRORS);
    InitializeCriticalSection(&csOutput);

    for (int i = 0; i < ROOT_COUNT; i++) {
        char rootPath[4] = {(char)('A' + i), ':', '\\', '\0'};
        strcpy_s(watchRootInfo[i].rootPath, 4, rootPath);
        watchRootInfo[i].bInitialized = false;
        watchRootInfo[i].bUsed = false;
    }

    char buffer[8192];
    while (true) {
        if (!gets_s(buffer, sizeof(buffer) - 1) || strcmp(buffer, "EXIT") == 0) {
            break;
        }

        if (strcmp(buffer, "ROOTS") == 0) {
            MarkAllRootsUnused();
            FreeWatchRootsList();

            bool failed = false;
            while (true) {
                if (!gets_s(buffer, sizeof(buffer) - 1)) {
                    failed = true;
                    break;
                }
                if (buffer[0] == '#') {
                    break;
                }

                char *pDriveLetter = buffer;
                if (*pDriveLetter == '|') pDriveLetter++;
                int driveLetter = toupper(*pDriveLetter);
                if (driveLetter >= 'A' && driveLetter <= 'Z') {
                    AddWatchRoot(pDriveLetter);
                    watchRootInfo[driveLetter - 'A'].bUsed = true;
                }
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
    return 0;
}
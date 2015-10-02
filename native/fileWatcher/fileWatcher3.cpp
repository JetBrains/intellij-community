/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <process.h>
#include <stdio.h>
#include <tchar.h>
#include <windows.h>

struct WatchRootInfo {
    char driveLetter;
    HANDLE hThread;
    HANDLE hStopEvent;
    bool bInitialized;
    bool bUsed;
    bool bFailed;
};

struct WatchRoot {
    char *path;
    WatchRoot *next;
};

const int ROOT_COUNT = 26;

WatchRootInfo watchRootInfos[ROOT_COUNT];

WatchRoot *firstWatchRoot = NULL;

CRITICAL_SECTION csOutput;

void NormalizeSlashes(char *path, char slash) {
    for (char *p = path; *p; p++)
        if (*p == '\\' || *p == '/')
            *p = slash;
}

// -- Watchable root checks ---------------------------------------------------

bool IsNetworkDrive(const char *name) {
    const int BUF_SIZE = 1024;
    char buffer[BUF_SIZE];
    UNIVERSAL_NAME_INFO *uni = (UNIVERSAL_NAME_INFO *) buffer;
    DWORD size = BUF_SIZE;

    DWORD result = WNetGetUniversalNameA(
            name,  // path for network resource
            UNIVERSAL_NAME_INFO_LEVEL, // level of information
            buffer, // name buffer
            &size // size of buffer
    );

    return result == NO_ERROR;
}

bool IsUnwatchableFS(const char *path) {
    char volumeName[MAX_PATH];
    char fsName[MAX_PATH];
    DWORD fsFlags;
    DWORD maxComponentLength;
    SetErrorMode(SEM_FAILCRITICALERRORS);
    if (!GetVolumeInformationA(path, volumeName, MAX_PATH - 1, NULL, &maxComponentLength, &fsFlags, fsName, MAX_PATH - 1))
        return false;
    if (strcmp(fsName, "NTFS") && strcmp(fsName, "FAT") && strcmp(fsName, "FAT32") && _stricmp(fsName, "exFAT") && _stricmp(fsName, "reFS"))
        return true;

    if (!strcmp(fsName, "NTFS") && maxComponentLength != 255 && !(fsFlags & FILE_SUPPORTS_REPARSE_POINTS)) {
        // SAMBA reports itself as NTFS
        return true;
    }

    return false;
}

bool IsWatchable(const char *path) {
    if (IsNetworkDrive(path))
        return false;
    if (IsUnwatchableFS(path))
        return false;
    return true;
}

// -- Substed drive checks ----------------------------------------------------

void PrintRemapForSubstDrive(char driveLetter) {
    const int BUF_SIZE = 1024;
    char targetPath[BUF_SIZE];

    char rootPath[8];
    sprintf_s(rootPath, 8, "%c:", driveLetter);

    DWORD result = QueryDosDeviceA(rootPath, targetPath, BUF_SIZE);
    if (result == 0) {
        return;
    }
    else {
        if (targetPath[0] == '\\' && targetPath[1] == '?' && targetPath[2] == '?' && targetPath[3] == '\\') {
            // example path: \??\C:\jetbrains\idea
            NormalizeSlashes(targetPath, '/');
            printf("%c:\n%s\n", driveLetter, targetPath + 4);
        }
    }
}

void PrintRemapForSubstDrives() {
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchRootInfos[i].bUsed) {
            PrintRemapForSubstDrive(watchRootInfos[i].driveLetter);
        }
    }
}

// -- Mount point enumeration -------------------------------------------------

const int BUFSIZE = 1024;

void PrintDirectoryReparsePoint(const char *path) {
    int size = (int)(strlen(path) + 2);
    char *directory = (char *) malloc(size);
    strcpy_s(directory, size, path);
    NormalizeSlashes(directory, '\\');
    if (directory[strlen(directory) - 1] != '\\')
        strcat_s(directory, size, "\\");

    char volumeName[_MAX_PATH];
    int rc = GetVolumeNameForVolumeMountPointA(directory, volumeName, sizeof(volumeName));
    if (rc) {
        char volumePathNames[_MAX_PATH];
        DWORD returnLength;
        rc = GetVolumePathNamesForVolumeNameA(volumeName, volumePathNames, sizeof(volumePathNames), &returnLength);
        if (rc) {
            char *p = volumePathNames;
            while (*p) {
                if (_stricmp(p, directory))   // if it's not the path we've already found
                {
                    NormalizeSlashes(directory, '/');
                    NormalizeSlashes(p, '/');
                    puts(directory);
                    puts(p);
                }
                p += strlen(p) + 1;
            }
        }
    }
    free(directory);
}

bool PrintMountPointsForVolume(HANDLE hVol, const char *volumePath, char *Buf) {
    HANDLE hPt;                  // handle for mount point scan
    char Path[BUFSIZE];          // string buffer for mount points
    DWORD dwSysFlags;            // flags that describe the file system
    char FileSysNameBuf[BUFSIZE];

    // Is this volume NTFS?
    GetVolumeInformationA(Buf, NULL, 0, NULL, NULL, &dwSysFlags, FileSysNameBuf, BUFSIZE);

    // Detect support for reparse points, and therefore for volume
    // mount points, which are implemented using reparse points.

    if (!(dwSysFlags & FILE_SUPPORTS_REPARSE_POINTS)) {
        return true;
    }

    // Start processing mount points on this volume.
    hPt = FindFirstVolumeMountPointA(
            Buf, // root path of volume to be scanned
            Path, // pointer to output string
            BUFSIZE // size of output buffer
    );

    // Shall we error out?
    if (hPt == INVALID_HANDLE_VALUE) {
        return GetLastError() != ERROR_ACCESS_DENIED;
    }

    // Process the volume mount point.
    char *buf = new char[MAX_PATH];
    do {
        strcpy_s(buf, MAX_PATH, volumePath);
        strcat_s(buf, MAX_PATH, Path);
        PrintDirectoryReparsePoint(buf);
    } while (FindNextVolumeMountPointA(hPt, Path, BUFSIZE));

    FindVolumeMountPointClose(hPt);
    return true;
}

bool PrintMountPoints(const char *path) {
    char volumeUniqueName[128];
    BOOL res = GetVolumeNameForVolumeMountPointA(path, volumeUniqueName, 128);
    if (!res) {
        return false;
    }

    char buf[BUFSIZE];            // buffer for unique volume identifiers
    HANDLE hVol;                  // handle for the volume scan

    // Open a scan for volumes.
    hVol = FindFirstVolumeA(buf, BUFSIZE);

    // Shall we error out?
    if (hVol == INVALID_HANDLE_VALUE) {
        return false;
    }

    bool success = true;
    do {
        if (!strcmp(buf, volumeUniqueName)) {
            success = PrintMountPointsForVolume(hVol, path, buf);
            if (!success) break;
        }
    } while (FindNextVolumeA(hVol, buf, BUFSIZE));

    FindVolumeClose(hVol);
    return success;
}

// -- Searching for mount points in watch roots (fallback) --------------------

void PrintDirectoryReparsePoints(const char *path) {
    char *const buf = _strdup(path);
    while (strchr(buf, '/')) {
        DWORD attributes = GetFileAttributesA(buf);
        if (attributes == INVALID_FILE_ATTRIBUTES)
            break;
        if (attributes & FILE_ATTRIBUTE_REPARSE_POINT) {
            PrintDirectoryReparsePoint(buf);
        }
        char *pSlash = strrchr(buf, '/');
        if (pSlash) {
            *pSlash = '\0';
        }
    }
    free(buf);
}

// This is called if we got an ERROR_ACCESS_DENIED when trying to enumerate all mount points for volume.
// In this case, we walk the directory tree up from each watch root, and look at each parent directory
// to check whether it's a reparse point.
void PrintWatchRootReparsePoints() {
    WatchRoot *pWatchRoot = firstWatchRoot;
    while (pWatchRoot) {
        PrintDirectoryReparsePoints(pWatchRoot->path);
        pWatchRoot = pWatchRoot->next;
    }
}

// -- Watcher thread ----------------------------------------------------------

void PrintChangeInfo(char *rootPath, FILE_NOTIFY_INFORMATION *info) {
    char FileNameBuffer[_MAX_PATH];
    int converted = WideCharToMultiByte(CP_ACP, 0, info->FileName, info->FileNameLength / sizeof(WCHAR), FileNameBuffer, _MAX_PATH - 1, NULL, NULL);
    FileNameBuffer[converted] = '\0';
    char *command;
    if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        command = "CREATE";
    }
    else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME) {
        command = "DELETE";
    }
    else if (info->Action == FILE_ACTION_MODIFIED) {
        command = "CHANGE";
    }
    else {
        return;  // unknown command
    }

    EnterCriticalSection(&csOutput);
    puts(command);
    printf("%s", rootPath);
    puts(FileNameBuffer);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

void PrintEverythingChangedUnderRoot(char *rootPath) {
    EnterCriticalSection(&csOutput);
    puts("RECDIRTY");
    puts(rootPath);
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

DWORD WINAPI WatcherThread(void *param) {
    WatchRootInfo *info = (WatchRootInfo *) param;

    OVERLAPPED overlapped;
    memset(&overlapped, 0, sizeof(overlapped));
    overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

    char rootPath[8];
    sprintf_s(rootPath, 8, "%c:\\", info->driveLetter);
    HANDLE hRootDir = CreateFileA(rootPath, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED, NULL);

    int buffer_size = 10240;
    char *buffer = new char[buffer_size];

    HANDLE handles[2];
    handles[0] = info->hStopEvent;
    handles[1] = overlapped.hEvent;
    while (true) {
        int rcDir = ReadDirectoryChangesW(hRootDir, buffer, buffer_size, TRUE,
                FILE_NOTIFY_CHANGE_FILE_NAME |
                        FILE_NOTIFY_CHANGE_DIR_NAME |
                        FILE_NOTIFY_CHANGE_ATTRIBUTES |
                        FILE_NOTIFY_CHANGE_SIZE |
                        FILE_NOTIFY_CHANGE_LAST_WRITE,
                NULL,
                &overlapped,
                NULL);
        if (rcDir == 0) {
            info->bFailed = true;
            break;
        }

        int rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
        if (rc == WAIT_OBJECT_0) {
            break;
        }
        if (rc == WAIT_OBJECT_0 + 1) {
            DWORD dwBytesReturned;
            if (!GetOverlappedResult(hRootDir, &overlapped, &dwBytesReturned, FALSE)) {
                info->bFailed = true;
                break;
            }

            if (dwBytesReturned == 0) {
                // don't send dirty too much, everything is changed anyway
                if (WaitForSingleObject(info->hStopEvent, 500) == WAIT_OBJECT_0)
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
    delete[] buffer;
    return 0;
}

// -- Roots update ------------------------------------------------------------

void MarkAllRootsUnused() {
    for (int i = 0; i < ROOT_COUNT; i++) {
        watchRootInfos[i].bUsed = false;
    }
}

void StartRoot(WatchRootInfo *info) {
    info->hStopEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    info->hThread = CreateThread(NULL, 0, &WatcherThread, info, 0, NULL);
    info->bInitialized = true;
}

void StopRoot(WatchRootInfo *info) {
    SetEvent(info->hStopEvent);
    WaitForSingleObject(info->hThread, INFINITE);
    CloseHandle(info->hThread);
    CloseHandle(info->hStopEvent);
    info->bInitialized = false;
}

void UpdateRoots(bool report) {
    char infoBuffer[256];
    strcpy_s(infoBuffer, "UNWATCHEABLE\n");
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchRootInfos[i].bInitialized && (!watchRootInfos[i].bUsed || watchRootInfos[i].bFailed)) {
            StopRoot(&watchRootInfos[i]);
            watchRootInfos[i].bFailed = false;
        }
        if (watchRootInfos[i].bUsed) {
            char rootPath[8];
            sprintf_s(rootPath, 8, "%c:\\", watchRootInfos[i].driveLetter);
            if (!IsWatchable(rootPath)) {
                strcat_s(infoBuffer, rootPath);
                strcat_s(infoBuffer, "\n");
                continue;
            }
            if (!watchRootInfos[i].bInitialized) {
                StartRoot(&watchRootInfos[i]);
            }
        }
    }

    if (!report) {
        return;
    }

    EnterCriticalSection(&csOutput);
    fprintf(stdout, "%s", infoBuffer);
    puts("#\nREMAP");
    PrintRemapForSubstDrives();
    bool printedMountPoints = true;
    for (int i = 0; i < ROOT_COUNT; i++) {
        if (watchRootInfos[i].bUsed) {
            char rootPath[8];
            sprintf_s(rootPath, 8, "%c:\\", watchRootInfos[i].driveLetter);
            if (!PrintMountPoints(rootPath))
                printedMountPoints = false;
        }
    }
    if (!printedMountPoints) {
        PrintWatchRootReparsePoints();
    }
    puts("#");
    fflush(stdout);
    LeaveCriticalSection(&csOutput);
}

void AddWatchRoot(const char *path) {
    WatchRoot *watchRoot = (WatchRoot *) malloc(sizeof(WatchRoot));
    watchRoot->next = NULL;
    watchRoot->path = _strdup(path);
    watchRoot->next = firstWatchRoot;
    firstWatchRoot = watchRoot;
}

void FreeWatchRootsList() {
    WatchRoot *pWatchRoot = firstWatchRoot;
    WatchRoot *pNext;
    while (pWatchRoot) {
        pNext = pWatchRoot->next;
        free(pWatchRoot->path);
        free(pWatchRoot);
        pWatchRoot = pNext;
    }
    firstWatchRoot = NULL;
}

// -- Main - filewatcher protocol ---------------------------------------------

int _tmain(int argc, _TCHAR *argv[]) {
    InitializeCriticalSection(&csOutput);

    for (int i = 0; i < 26; i++) {
        watchRootInfos[i].driveLetter = 'A' + i;
        watchRootInfos[i].bInitialized = false;
        watchRootInfos[i].bUsed = false;
    }

    char buffer[8192];
    while (true) {
        if (!gets_s(buffer, sizeof(buffer) - 1))
            break;

        if (!strcmp(buffer, "ROOTS")) {
            MarkAllRootsUnused();
            FreeWatchRootsList();
            bool failed = false;
            while (true) {
                if (!gets_s(buffer, sizeof(buffer) - 1)) {
                    failed = true;
                    break;
                }
                if (buffer[0] == '#')
                    break;
                int driveLetterPos = 0;
                char *pDriveLetter = buffer;
                if (*pDriveLetter == '|')
                    pDriveLetter++;

                AddWatchRoot(pDriveLetter);

                _strupr_s(buffer, sizeof(buffer) - 1);
                char driveLetter = *pDriveLetter;
                if (driveLetter >= 'A' && driveLetter <= 'Z') {
                    watchRootInfos[driveLetter - 'A'].bUsed = true;
                }
            }
            if (failed)
                break;

            UpdateRoots(true);
        }

        if (!strcmp(buffer, "EXIT"))
            break;
    }

    MarkAllRootsUnused();
    UpdateRoots(false);

    DeleteCriticalSection(&csOutput);
}

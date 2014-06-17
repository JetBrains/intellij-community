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

#include <CoreServices/CoreServices.h>
#include <pthread.h>
#include <stdio.h>
#include <strings.h>
#include <sys/mount.h>

#define PRIVATE_DIR "/private/"
#define PRIVATE_LEN 9

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static bool report_private = true;

static void reportEvent(char *event, char *path) {
    size_t len = 0;
    if (path != NULL) {
        len = strlen(path);
        for (char* p = path; *p != '\0'; p++) {
            if (*p == '\n') {
                *p = '\0';
            }
        }
    }

    pthread_mutex_lock(&lock);
    if (path == NULL || report_private || strncasecmp(path, PRIVATE_DIR, PRIVATE_LEN) != 0) {
        fputs(event, stdout);
        fputc('\n', stdout);
        if (path != NULL) {
            fwrite(path, len, 1, stdout);
            fputc('\n', stdout);
        }
        fflush(stdout);
    }
    pthread_mutex_unlock(&lock);
}

static void callback(ConstFSEventStreamRef streamRef,
                     void *clientCallBackInfo,
                     size_t numEvents,
                     void *eventPaths,
                     const FSEventStreamEventFlags eventFlags[],
                     const FSEventStreamEventId eventIds[]) {
    char **paths = eventPaths;

    for (int i = 0; i < numEvents; i++) {
        // TODO[max] Lion has much more detailed flags we need accurately process. For now just reduce to SL events range.
        FSEventStreamEventFlags flags = eventFlags[i] & 0xFF;
        if ((flags & kFSEventStreamEventFlagMustScanSubDirs) != 0) {
            reportEvent("RECDIRTY", paths[i]);
        }
        else if (flags != kFSEventStreamEventFlagNone) {
            reportEvent("RESET", NULL);
        }
        else {
            reportEvent("DIRTY", paths[i]);
        }
    }
}

static void * EventProcessingThread(void *data) {
    FSEventStreamRef stream = (FSEventStreamRef) data;
    FSEventStreamScheduleWithRunLoop(stream, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    FSEventStreamStart(stream);
    CFRunLoopRun();
    return NULL;
}

static void PrintMountedFileSystems(CFArrayRef roots) {
    int fsCount = getfsstat(NULL, 0, MNT_WAIT);
    if (fsCount == -1) return;

    struct statfs fs[fsCount];
    fsCount = getfsstat(fs, (int)(sizeof(struct statfs) * fsCount), MNT_NOWAIT);
    if (fsCount == -1) return;

    CFMutableArrayRef mounts = CFArrayCreateMutable(NULL, 0, NULL);

    for (int i = 0; i < fsCount; i++) {
        if ((fs[i].f_flags & MNT_LOCAL) != MNT_LOCAL) {
            char *mount = fs[i].f_mntonname;
            size_t mountLen = strlen(mount);

            for (int j = 0; j < CFArrayGetCount(roots); j++) {
                char *root = (char *)CFArrayGetValueAtIndex(roots, j);
                size_t rootLen = strlen(root);

                if (rootLen >= mountLen && strncmp(root, mount, mountLen) == 0) {
                    // root under mount point
                    if (rootLen == mountLen || root[mountLen] == '/' || strcmp(mount, "/") == 0) {
                        CFArrayAppendValue(mounts, root);
                    }
                }
                else if (strncmp(root, mount, rootLen) == 0) {
                    // root over mount point
                    if (strcmp(root, "/") == 0 || mount[rootLen] == '/') {
                        CFArrayAppendValue(mounts, mount);
                    }
                }
            }
        }
    }

    pthread_mutex_lock(&lock);
    printf("UNWATCHEABLE\n");
    for (int i = 0; i < CFArrayGetCount(mounts); i++) {
        char *mount = (char *)CFArrayGetValueAtIndex(mounts, i);
        printf("%s\n", mount);
    }
    printf("#\n");
    fflush(stdout);
    pthread_mutex_unlock(&lock);

    CFRelease(mounts);
}

// Static buffer for fscanf. All of the are being performed from a single thread, so it's thread safe.
static char command[2048];

static void ParseRoots() {
    CFMutableArrayRef roots = CFArrayCreateMutable(NULL, 0, NULL);
    bool has_private_root = false;

    while (TRUE) {
        fscanf(stdin, "%s", command);
        if (strcmp(command, "#") == 0 || feof(stdin)) break;
        char* path = command[0] == '|' ? command + 1 : command;
        CFArrayAppendValue(roots, strdup(path));
        if (strcmp(path, "/") == 0 || strncasecmp(path, PRIVATE_DIR, PRIVATE_LEN) == 0) {
            has_private_root = true;
        }
    }

    pthread_mutex_lock(&lock);
    report_private = has_private_root;
    pthread_mutex_unlock(&lock);

    PrintMountedFileSystems(roots);

    for (int i = 0; i < CFArrayGetCount(roots); i++) {
        void *value = (char *)CFArrayGetValueAtIndex(roots, i);
        free(value);
    }
    CFRelease(roots);
}

int main(const int argc, const char* argv[]) {
    // Checking if necessary API is available (need MacOS X 10.5 or later).
    if (FSEventStreamCreate == NULL) {
        printf("GIVEUP\n");
        return 1;
    }

    CFStringRef path = CFSTR("/");
    CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void **)&path, 1, NULL);
    CFAbsoluteTime latency = 0.3;  // Latency in seconds
    FSEventStreamRef stream = FSEventStreamCreate(
        NULL,
        &callback,
        NULL,
        pathsToWatch,
        kFSEventStreamEventIdSinceNow,
        latency,
        kFSEventStreamCreateFlagNoDefer
    );
    if (stream == NULL) {
        printf("GIVEUP\n");
        return 2;
    }

    pthread_t threadId;
    if (pthread_create(&threadId, NULL, EventProcessingThread, stream) != 0) {
        printf("GIVEUP\n");
        return 3;
    }

    while (TRUE) {
        fscanf(stdin, "%s", command);
        if (strcmp(command, "EXIT") == 0 || feof(stdin)) break;
        if (strcmp(command, "ROOTS") == 0) ParseRoots();
    }

    return 0;
}

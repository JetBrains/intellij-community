/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
#include <sys/mount.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

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
            pthread_mutex_lock(&lock);
            printf("RECDIRTY\n%s\n", paths[i]);
            fflush(stdout);
            pthread_mutex_unlock(&lock);
        }
        else if (flags != kFSEventStreamEventFlagNone) {
            pthread_mutex_lock(&lock);
            printf("RESET\n");
            fflush(stdout);
            pthread_mutex_unlock(&lock);
        }
        else {
            pthread_mutex_lock(&lock);
            printf("DIRTY\n%s\n", paths[i]);
            fflush(stdout);
            pthread_mutex_unlock(&lock);
        }
    }
}

static void * EventProcessingThread(void *data) {
    CFStringRef path = CFSTR("/");
    CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void **)&path, 1, NULL);
    void *callbackInfo = NULL;
    CFAbsoluteTime latency = 0.3;  // Latency in seconds

    FSEventStreamRef stream = FSEventStreamCreate(
        NULL,
        &callback,
        callbackInfo,
        pathsToWatch,
        kFSEventStreamEventIdSinceNow,
        latency,
        kFSEventStreamCreateFlagNoDefer
    );

    FSEventStreamScheduleWithRunLoop(stream, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    FSEventStreamStart(stream);

    CFRunLoopRun();
    return NULL;
}

#define FS_FLAGS (MNT_LOCAL|MNT_JOURNALED)

static void PrintMountedFileSystems(CFArrayRef roots) {
    int fsCount = getfsstat(NULL, 0, MNT_WAIT);
    if (fsCount == -1) return;

    struct statfs fs[fsCount];
    fsCount = getfsstat(fs, sizeof(struct statfs) * fsCount, MNT_NOWAIT);
    if (fsCount == -1) return;

    CFMutableArrayRef mounts = CFArrayCreateMutable(NULL, 0, NULL);

    for (int i = 0; i < fsCount; i++) {
        if ((fs[i].f_flags & FS_FLAGS) != FS_FLAGS) {
            char *mount = fs[i].f_mntonname;
            int mountLen = strlen(mount);

            for (int j = 0; j < CFArrayGetCount(roots); j++) {
                char *root = (char *)CFArrayGetValueAtIndex(roots, j);
                int rootLen = strlen(root);

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

    while (TRUE) {
        fscanf(stdin, "%s", command);
        if (strcmp(command, "#") == 0 || feof(stdin)) break;
        char* path = command[0] == '|' ? command + 1 : command;
        CFArrayAppendValue(roots, strdup(path));
    }

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

    pthread_t threadId;
    if (pthread_create(&threadId, NULL, EventProcessingThread, NULL) != 0) {
        // Give up if cannot create a thread.
        printf("GIVEUP\n");
        return 2;
    }

    while (TRUE) {
        fscanf(stdin, "%s", command);
        if (strcmp(command, "EXIT") == 0 || feof(stdin)) break;
        if (strcmp(command, "ROOTS") == 0) ParseRoots();
    }

    return 0;
}

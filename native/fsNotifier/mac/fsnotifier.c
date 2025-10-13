// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#include <pthread.h>
#include <stdio.h>
#include <strings.h>
#include <sys/mount.h>
#include <CoreServices/CoreServices.h>

#define PRIVATE_DIR "/private/"
#define PRIVATE_LEN 9

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static bool report_private = true;

static void reportEvent(char *event, char *path) {
    size_t len = 0;
    if (path != NULL) {
        len = strlen(path);
        for (char *p = path; *p != '\0'; p++) {
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

static void callback(__unused ConstFSEventStreamRef streamRef,
                     __unused void *clientCallBackInfo,
                     size_t numEvents,
                     void *eventPaths,
                     const FSEventStreamEventFlags eventFlags[],
                     __unused const FSEventStreamEventId eventIds[]) {
    char **paths = eventPaths;

    for (size_t i = 0; i < numEvents; i++) {
        FSEventStreamEventFlags flags = eventFlags[i] & 0xFF;
        if ((flags & kFSEventStreamEventFlagMustScanSubDirs) != 0) {
            reportEvent("RECDIRTY", paths[i]);
        } else if (flags != kFSEventStreamEventFlagNone) {
            reportEvent("RESET", NULL);
        } else {
            reportEvent("DIRTY", paths[i]);
        }
    }
}

static void *EventProcessingThread(void *data) {
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
                } else if (strncmp(root, mount, rootLen) == 0) {
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

#define INPUT_BUF_LEN 2048
static char input_buf[INPUT_BUF_LEN];

static char *read_stdin(void) {
    char* result = fgets(input_buf, INPUT_BUF_LEN, stdin);
    if (result == NULL || feof(stdin)) {
        return NULL;
    }
    size_t length = strlen(input_buf);
    if (length > 0 && input_buf[length - 1] == '\n') {
        input_buf[length - 1] = '\0';
    }
    return input_buf;
}

static bool ParseRoots(void) {
    CFMutableArrayRef roots = CFArrayCreateMutable(NULL, 0, NULL);
    bool has_private_root = false;

    while (TRUE) {
        char *command = read_stdin();
        if (command == NULL) return false;
        if (strcmp(command, "#") == 0) break;
        char *path = command[0] == '|' ? command + 1 : command;
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
    return true;
}

int main(void) {
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
        return 1;
    }

    pthread_t threadId;
    if (pthread_create(&threadId, NULL, EventProcessingThread, stream) != 0) {
        printf("GIVEUP\n");
        return 2;
    }

    while (TRUE) {
        char *command = read_stdin();
        if (command == NULL || strcmp(command, "EXIT") == 0) break;
        if (strcmp(command, "ROOTS") == 0) {
            if (!ParseRoots()) break;
        }
    }

    return 0;
}

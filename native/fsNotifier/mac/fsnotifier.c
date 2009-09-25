#include <CoreServices/CoreServices.h>
#include <sys/mount.h>

static int ReportMountedFileSystems()
    // If fsBuf is too small to account for all volumes, getfsstat will 
    // silently truncate the returned information.  Worse yet, it returns 
    // the number of volumes it passed back, not the number of volumes present, 
    // so you can't tell if the list was truncated. 
    //
    // So, in order to get an accurate snapshot of the volume list, I call 
    // getfsstat with a NULL fsBuf to get a count (fsCountOrig), then allocate a 
    // buffer that holds (fsCountOrig + 1) items, then call getfsstat again with 
    // that buffer.  If the list was silently truncated, the second count (fsCount)
    // will be (fsCountOrig + 1), and we loop to try again.
{
    int                 err;
    int                 fsCountOrig;
    int                 fsCount;
    struct statfs *     fsBuf;
    bool                done;


    fsBuf = NULL;
    fsCount = 0;
    
    done = false;
    do {
        // Get the initial count.
        err = 0;
        fsCountOrig = getfsstat(NULL, 0, MNT_WAIT);
        if (fsCountOrig < 0) {
            err = errno;
        }
        
        // Allocate a buffer for fsCountOrig + 1 items.
        if (err == 0) {
            if (fsBuf != NULL) {
                free(fsBuf);
            }
            fsBuf = malloc((fsCountOrig + 1) * sizeof(*fsBuf));
            if (fsBuf == NULL) {
                err = ENOMEM;
            }
        }
        
        // Get the list.  
        if (err == 0) {
            fsCount = getfsstat(fsBuf, (int) ((fsCountOrig + 1) * sizeof(*fsBuf)), MNT_WAIT);
            if (fsCount < 0) {
                err = errno;
            }
        }
        
        // We got the full list if the number of items returned by the kernel 
        // is strictly less than the buffer that we allocated (fsCountOrig + 1).
        if (err == 0) {
            if (fsCount <= fsCountOrig) {
                done = true;
            }
        }
    } while ( (err == 0) && ! done );

    int i;
    int mountCounts = 0;
    for (i = 0; i < fsCount; i++) {
        if ((fsBuf[i].f_flags & MNT_LOCAL) == 0 || (fsBuf[i].f_flags & MNT_JOURNALED) == 0) {
            if (mountCounts == 0) {
              printf("UNWATCHEABLE\n");
            }
            printf("%s\n", fsBuf[i].f_mntonname);
            mountCounts++;
        }
    }

    if (mountCounts > 0) {
      printf("#\n");
      fflush(stdout);
    }

    free(fsBuf);
    fsBuf = NULL;
    
    return err;
}

void callback(ConstFSEventStreamRef streamRef,
              void *clientCallBackInfo,
              size_t numEvents,
              void *eventPaths,
              const FSEventStreamEventFlags eventFlags[],
              const FSEventStreamEventId eventIds[]) {
    char **paths = eventPaths;
 
    int i;
    for (i=0; i<numEvents; i++) {
      FSEventStreamEventFlags flags = eventFlags[i];
      if (flags == kFSEventStreamEventFlagMount || flags == kFSEventStreamEventFlagUnmount) {
        ReportMountedFileSystems();
      }
      else if ((flags & kFSEventStreamEventFlagMustScanSubDirs) != 0) {
        printf("RECDIRTY\n");
        printf("%s\n", paths[i]);
      }
      else if (eventFlags[i] != kFSEventStreamEventFlagNone) {
        printf("RESET\n");
      }
      else {
        printf("DIRTY\n");
        printf("%s\n", paths[i]);
      }
    }

    fflush(stdout);
}

// Static buffer for fscanf. All of the are being performed from a single thread, so it's thread safe.
static char command[2048];

static void parseRoots() {
    while (TRUE) {
     fscanf(stdin, "%s", command);
     if (strcmp(command, "#") == 0 || feof(stdin)) break;
    }
}

void *event_processing_thread(void *data) {
    CFStringRef mypath = CFSTR("/");
    CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void **)&mypath, 1, NULL);
    void *callbackInfo = NULL;

    FSEventStreamRef stream;
    CFAbsoluteTime latency = 0.3; /* Latency in seconds */
 
    // Create the stream, passing in a callback,
    stream = FSEventStreamCreate(NULL,
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

int main (int argc, const char * argv[]) {
    // Checking if necessary API is available (need MacOS X 10.5 or later).
    if (FSEventStreamCreate == NULL) {
      printf("GIVEUP\n");
      return 1;
    }

    ReportMountedFileSystems();

    pthread_t thread_id;
    int rc = pthread_create(&thread_id, NULL, event_processing_thread, NULL);

    if (rc != 0) {
      // Give up if cannot create a thread.
      printf("GIVEUP\n");
      exit(1);
    }

    while (TRUE) {
      fscanf(stdin, "%s", command); 
      if (strcmp(command, "EXIT") == 0 || feof(stdin)) exit(0);
      if (strcmp(command, "ROOTS") == 0) parseRoots();
    }
 
    return 0;
}

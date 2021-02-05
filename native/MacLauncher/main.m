// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "Launcher.h"
#include "rosetta.h"

#define FOREVER ((CFTimeInterval) 1e20)

static void timer_empty(__unused CFRunLoopTimerRef timer, __unused void *info) {
}

static void parkRunLoop() {
    CFRunLoopTimerRef t = CFRunLoopTimerCreate(kCFAllocatorDefault, FOREVER, (CFTimeInterval)0.0, 0, 0, timer_empty, NULL);
    CFRunLoopAddTimer(CFRunLoopGetCurrent(), t, kCFRunLoopDefaultMode);
    CFRelease(t);

    SInt32 result;
    do {
        result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, FOREVER, false);
    } while (result != kCFRunLoopRunFinished);
}

static void makeSameStackSize(NSThread *thread) {
    struct rlimit l;
    int err = getrlimit(RLIMIT_STACK, &l);
    if (err == ERR_SUCCESS && l.rlim_cur > 0) {
        thread.stackSize = (NSUInteger) l.rlim_cur;
    }
}

static void launchInNewThread(Launcher *launcher) {
   NSThread *thread = [[[NSThread alloc] initWithTarget:launcher selector:@selector(launch) object:nil] autorelease];
   makeSameStackSize(thread);
   [thread start];

}

int main(int argc, char *argv[]) {
    if (argc > 1) {
        if (strcmp(argv[1], ROSETTA_CHECK_COMMAND) == 0) return checkRosetta();
#ifndef NDEBUG
        if (strcmp(argv[1], ROSETTA_REQUEST_COMMAND) == 0) return requestRosetta(@(argv[0]));
#endif
    }

    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    if (validationJavaVersion()){
        launchInNewThread([[[Launcher alloc] initWithArgc:argc argv:argv] autorelease]);
        parkRunLoop();
    }
    [pool release];
    return 0;
}

#import "Launcher.h"

#define FOREVER ((CFTimeInterval) 1e20)

static void timer_empty(CFRunLoopTimerRef timer, void *info) {
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
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    if (validationJavaVersion()){
        launchInNewThread([[[Launcher alloc] initWithArgc:argc argv:argv] autorelease]);
        parkRunLoop();
    }
    [pool release];
    return 0;
}

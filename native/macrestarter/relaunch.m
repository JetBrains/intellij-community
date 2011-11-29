// gcc -Wall -arch i386 -arch ppc -mmacosx-version-min=10.4 -Os -framework AppKit -o relaunch relaunch.m

#import <AppKit/AppKit.h>

int main(int argc, const char *argv[]) {
    if (argc != 2) return EXIT_FAILURE;

    unsigned int interval = 500; // check every 0.5 second
    unsigned int slept = 0;
    while (getppid() != 1) {
        usleep(interval * 1000);

        slept += interval;
        // if (slept > 10 * 1000 /* wait for maximum 10 seconds */) return EXIT_FAILURE;
    }

    char const *pathToRelaunch = argv[1];
    [[NSWorkspace sharedWorkspace] launchApplication:[NSString stringWithUTF8String:pathToRelaunch]];

    return EXIT_SUCCESS;
}
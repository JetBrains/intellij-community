// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "rosetta.h"
#import <dlfcn.h>
#import <sysexits.h>
#import <AppKit/AppKit.h>

int checkRosetta(void) {
    char *error;
    void *handle = dlopen(ROSETTA_DYLIB, RTLD_LAZY | RTLD_LOCAL);
    if ((error = dlerror()) != NULL) {
        fprintf(stderr, "%s\n", error);
        return EX_OSERR;
    }

    BOOL (*check_rosetta_installed)() = (BOOL (*)()) dlsym(handle, ROSETTA_FUNCTION_NAME);
    if ((error = dlerror()) != NULL) {
        fprintf(stderr, "%s\n", error);
        return EX_OSERR;
    }

    BOOL has_rosetta = check_rosetta_installed();
    fprintf(stderr, "%s: %s\n", ROSETTA_FUNCTION_NAME, has_rosetta ? "true" : "false");
    return has_rosetta ? EX_OK : EX_UNAVAILABLE;
}

int runAndWait(NSString *path, NSArray<NSString*> *arguments) {
    NSTask *task = [NSTask launchedTaskWithLaunchPath:path arguments:arguments];
    [task waitUntilExit];
    int status = task.terminationStatus;
    [task release];
    return status;
}

int requestRosetta(NSString* launcherPath) {
    int checkRosetta = runAndWait(launcherPath, @[@(ROSETTA_CHECK_COMMAND)]);
    if (checkRosetta != EX_UNAVAILABLE) return checkRosetta;

    NSString *openBinary = @"/usr/bin/open", *waitFlag = @"-W";
    if (runAndWait(openBinary, @[waitFlag, @"-b", @(ROSETTA_INSTALLER_BUNDLE_IDENTIFIER)]) != 0) {
        if (runAndWait(openBinary, @[waitFlag, @(ROSETTA_INSTALLER_FALLBACK_URL)]) != 0) return EX_OSERR;
    }

    return EX_OK;
}

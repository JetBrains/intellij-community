// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "utils.h"

NSString *readFile(NSString *path) {
    NSError *err = nil;
    NSString *contents = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&err];
    if (contents == nil) {
        debugLog([NSString stringWithFormat:@"Reading at %@ failed, Error is: %@", path, err.localizedDescription]);
        return nil;
    }

    debugLog([NSString stringWithFormat: @"Reading at %@ OK", path]);

    return contents;
}

NSString *trim(NSString *line) {
    return [line stringByTrimmingCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@" \t"]];
}

BOOL isDebugEnabled() {
    return getenv("IDEA_LAUNCHER_DEBUG") != NULL;
}

void debugLog(NSString *message) {
    if (isDebugEnabled()) {
        NSLog(@"%@", message);
    }
}

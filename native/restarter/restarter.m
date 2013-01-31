/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

#import <AppKit/AppKit.h>

int main(int argc, const char *argv[]) {
    if (argc < 2) return EXIT_FAILURE;

    unsigned int interval = 500; // check every 0.5 second
    unsigned int slept = 0;
    while (getppid() != 1) {
        usleep(interval * 1000);

        slept += interval;
    }

    if (argc > 2) {
        NSString *launchPath = [NSString stringWithUTF8String:argv[2]];
        NSMutableArray *arguments = [NSMutableArray array];
        for (int i = 3; i < argc; i++) {
            [arguments addObject:[NSString stringWithUTF8String:argv[i]]];
        }
        NSTask *task = [NSTask launchedTaskWithLaunchPath:launchPath arguments:arguments];
        [task waitUntilExit];
    }

    NSString *pathToRelaunch = [NSString stringWithUTF8String:argv[1]];
    [[NSWorkspace sharedWorkspace] launchApplication:pathToRelaunch];

    return EXIT_SUCCESS;
}
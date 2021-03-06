// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import <Foundation/Foundation.h>


NSString *readFile(NSString *path);
NSString *trim(NSString *line);

void debugLog(NSString *message);
BOOL isDebugEnabled();

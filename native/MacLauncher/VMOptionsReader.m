// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "VMOptionsReader.h"
#import "utils.h"


@implementation VMOptionsReader {
}

+ (NSArray *)readFile:(NSString *)path {
    NSMutableArray *answer = [NSMutableArray array];

    NSString *contents = readFile(path);
    if (contents) {
        NSArray *lines = [contents componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
        for (NSString *line in lines) {
            NSString *trimmedLine = trim(line);
            if ([trimmedLine length] > 0) {
                if ([trimmedLine characterAtIndex:0] != '#') {
                    [answer addObject:trimmedLine];
                }
            }
        }

        return answer;
    }

    return nil;
}

@end

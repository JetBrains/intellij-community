// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "PropertyFileReader.h"
#import "utils.h"


@implementation PropertyFileReader {
}

+ (void)parseProperty:(NSString *)string to:(NSMutableDictionary *)to {
    NSRange delimiter = [string rangeOfString:@"="];
    if (delimiter.length > 0 && delimiter.location + 1 <= string.length) {
        NSString *key = [string substringToIndex:delimiter.location];
        NSString *value=[string substringFromIndex:delimiter.location + 1];
        to[key] = value;
    }
}

+ (NSDictionary *)readFile:(NSString *)path {
    NSMutableDictionary *answer = [NSMutableDictionary dictionary];

    NSString *contents = readFile(path);

    if (contents) {
        NSArray *lines = [contents componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
        for (NSString *line in lines) {
            NSString *trimmedLine = trim(line);
            if ([trimmedLine length] > 0) {
                if ([trimmedLine characterAtIndex:0] != '#') {
                    [self parseProperty:trimmedLine to:answer];
                }
            }
        }

        return answer;
    }

    return nil;
}

@end

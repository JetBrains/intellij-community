//
// Created by max on 11/6/12.
//
// To change the template use AppCode | Preferences | File Templates.
//


#import "VMOptionsReader.h"
#import "utils.h"


@implementation VMOptionsReader {

}
+ (NSArray *)readFile:(NSString *)path {
    NSMutableArray *answer = [NSMutableArray array];

    NSString *contents = readFile(path);
    if (contents) {
        [contents enumerateLinesUsingBlock:^(NSString *line, BOOL *stop) {
            NSString *trimmedLine = trim(line);
            if ([trimmedLine length] > 0) {
                if ([trimmedLine characterAtIndex:0] != '#') {
                    [answer addObject:trimmedLine];
                }
            }
        }];
        return answer;
    }

    return nil;
}

@end
//
// Created by max on 11/6/12.
//
// To change the template use AppCode | Preferences | File Templates.
//


#import <Foundation/Foundation.h>


@interface PropertyFileReader : NSObject
+ (NSDictionary *)readFile:(NSString *)path;
@end
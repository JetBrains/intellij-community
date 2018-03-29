#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

@interface ItemDesc : NSObject
-(nullable __kindof NSTouchBarItem *)create;             // NOTE: abstract, must create autorelease TB-items
@property (retain, nonatomic) NSString * uid;
@end

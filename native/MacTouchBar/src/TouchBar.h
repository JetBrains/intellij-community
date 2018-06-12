#import <Cocoa/Cocoa.h>

@interface TouchBar : NSObject<NSTouchBarDelegate>
@property (retain, nonatomic) NSTouchBar * touchBar;
@property (retain, nonatomic) NSString * name; // for debugging/logging
@end

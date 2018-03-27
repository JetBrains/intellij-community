#import <Cocoa/Cocoa.h>

@interface TouchBar : NSObject<NSTouchBarDelegate>
@property (readonly) NSTouchBar * touchBar;
@end

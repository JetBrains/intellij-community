#import "SpacingDesc.h"

@implementation SpacingDesc
- (id)init:(NSString *)type {
    self = [super init];
    if (self) {
        if ([type isEqualToString:@"small"]) {
            self.uid = NSTouchBarItemIdentifierFixedSpaceSmall;
        } else if ([type isEqualToString:@"large"]) {
            self.uid = NSTouchBarItemIdentifierFixedSpaceLarge;
        } else if ([type isEqualToString:@"flexible"]) {
            self.uid = NSTouchBarItemIdentifierFlexibleSpace;
        }
    }
    return self;
}
@end

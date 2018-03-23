#import "ItemDesc.h"
#import "Utils.h"

@implementation ItemDesc
@synthesize uid;
-(nullable __kindof NSTouchBarItem *)create {
    nserror(@"ItemDesc.create mustn't be called");
    return nil;
}
@end

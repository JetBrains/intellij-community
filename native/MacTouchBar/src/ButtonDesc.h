#import "ItemDesc.h"
#import "JTypes.h"

@interface ButtonDesc : ItemDesc
- (id) init:(NSImage *)img text:(NSString*)text act:(execute)act;
- (void) doAction;
@end

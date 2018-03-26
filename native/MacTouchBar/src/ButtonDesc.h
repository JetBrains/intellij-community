#import "ItemDesc.h"
#import "JTypes.h"

@interface ButtonDesc : ItemDesc
- (id)init:(execute)act;
- (void)doAction;
@end

@interface ButtonImgDesc : ButtonDesc
- (id)init:(NSImage *)img act:(execute)act;
@end

@interface ButtonTextDesc : ButtonDesc
- (id)init:(NSString*)text act:(execute)act;
@end

@interface ButtonImgTextDesc : ButtonDesc
- (id)init:(NSImage *)img text:(NSString*)text act:(execute)act;
@end

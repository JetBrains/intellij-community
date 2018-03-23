#import "ItemDesc.h"
#import "JTypes.h"

@interface ScrubberDesc : ItemDesc <NSScrubberDataSource, NSScrubberDelegate, NSScrubberFlowLayoutDelegate>
- (id)init:(int)scrubW;
@end

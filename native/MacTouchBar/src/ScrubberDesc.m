#import "ScrubberDesc.h"
#import "ScrubberItemView.h"
#import "Utils.h"

static const NSUserInterfaceItemIdentifier g_scrubberItemIdentifier = @"scrubberItem";
static const int g_heightOfTouchBar = 30;
static const int g_interItemSpacings = 5;

@interface ScrubberItemData : NSObject {
    execute _action;
}
- (id) init:(NSImage *)img text:(NSString *)text action:(execute)act;
- (void) doAction;
@property (retain, nonatomic) NSImage * img;
@property (retain, nonatomic) NSString * text;
@end

@implementation ScrubberItemData
-(id) init:(NSImage *)img text:(NSString *)text action:(execute)act {
    self = [super init];
    if (self) {
        _action = act;
        self.img = img;
        self.text = text;
    }
    return self;
}
- (void)doAction{
    (*_action)();
}
@end

@interface ScrubberDesc() {
    int _width;
}
- (void) addItem:(NSImage *)img text:(NSString *)text action:(execute)act;
@property (retain, nonatomic) NSMutableArray<ScrubberItemData *> * items;
@end

@implementation ScrubberDesc

@synthesize items;

- (id)init:(int)scrubW {
    self = [super init];
    if (self) {
        _width = scrubW;
        self.items = [[[NSMutableArray alloc] init] autorelease];
    }
    return self;
}

- (NSInteger)numberOfItemsForScrubber:(NSScrubber *)scrubber {
    return items.count;
}

- (void)addItem:(NSImage *)img text:(NSString *)text action:(execute)act {
    [items addObject:[[[ScrubberItemData alloc]init:img text:text action:act] autorelease]];
}


- (NSScrubberItemView *)scrubber:(NSScrubber *)scrubber viewForItemAtIndex:(NSInteger)index {
    nstrace(@"create viewForItemAtIndex %d", index);
    if (index >= items.count) {
        nserror(@"viewForItemAtIndex: index %d is exceeds count of items %d", index, items.count);
        return nil;
    }
    ScrubberItemView *itemView = [scrubber makeItemWithIdentifier:g_scrubberItemIdentifier owner:nil];
    ScrubberItemData * data = items[index];
    if (data == NULL)
        return nil;

    [itemView setImgAndText:data.img text:data.text];
    return itemView;
}

- (NSSize)scrubber:(NSScrubber *)scrubber layout:(NSScrubberFlowLayout *)layout sizeForItemAtIndex:(NSInteger)itemIndex {
    if (itemIndex >= items.count) {
        nserror(@"sizeForItemAtIndex: index %d is exceeds count of items %d", itemIndex, items.count);
        return NSMakeSize(0, 0);
    }
    ScrubberItemData * data = items[itemIndex];
    if (data == NULL)
        return NSMakeSize(0, 0);

    NSFont * font = [NSFont systemFontOfSize:0]; // Specify a system font size of 0 to automatically use the appropriate size.
    NSSize txtSize = [data.text sizeWithAttributes:@{ NSFontAttributeName:font }];
    CGFloat width = txtSize.width + data.img.size.width + 2*g_marginBorders + g_marginImgText + 13/*empiric diff for textfield paddings*/; // TODO: get rid of empiric, use size obtained from NSTextField

    nstrace(@"sizeForItemAtIndex: txt='%@', iconW=%d, txt size = %1.2f, %1.2f, result width = %1.2f", data.text, data.img.size.width, txtSize.width, txtSize.height, width);
    return NSMakeSize(width, g_heightOfTouchBar);
}

- (void)scrubber:(NSScrubber *)scrubber didSelectItemAtIndex:(NSInteger)selectedIndex {
    if (selectedIndex >= items.count) {
        nserror(@"didSelectItemAtIndex: index %d is exceeds count of items %d", selectedIndex, items.count);
        return;
    }
    ScrubberItemData * data = items[selectedIndex];
    if (data == NULL)
        return;

    nstrace(@"perform action of scrubber item at %d", selectedIndex);
    [data doAction];
}

- (nullable __kindof NSTouchBarItem *)create {
    nstrace(@"create scrubber %@", self.uid);

    NSScrubber *scrubber = [[[NSScrubber alloc] initWithFrame:NSMakeRect(0, 0, _width, g_heightOfTouchBar)] autorelease];
    scrubber.delegate = self;
    scrubber.dataSource = self;

    [scrubber registerClass:[ScrubberItemView class] forItemIdentifier:g_scrubberItemIdentifier];

    scrubber.showsAdditionalContentIndicators = NO;// For the image scrubber, we want the control to draw a fade effect to indicate that there is additional unscrolled content.
    scrubber.selectedIndex = 0;

    NSScrubberFlowLayout *scrubberLayout = [[[NSScrubberFlowLayout alloc] init] autorelease];
    scrubberLayout.itemSpacing = g_interItemSpacings;
    [scrubberLayout invalidateLayout];
    scrubber.scrubberLayout = scrubberLayout;

    scrubber.mode = NSScrubberModeFree;
    scrubber.showsArrowButtons = NO;
    scrubber.selectionOverlayStyle = nil;
    [scrubber.widthAnchor constraintGreaterThanOrEqualToConstant:_width].active = YES;

    NSCustomTouchBarItem * scrubberItem = [[[NSCustomTouchBarItem alloc] initWithIdentifier:self.uid] autorelease];
    scrubberItem.view = scrubber;
    return scrubberItem;
}

@end

void addScrubberItem(id scrubObj, char* text, char* raster4ByteRGBA, int w, int h, execute action) {
    [(ScrubberDesc *)scrubObj addItem:createImgFrom4ByteRGBA((unsigned char *)raster4ByteRGBA, w, h) text:[NSString stringWithUTF8String:text] action:action];
}
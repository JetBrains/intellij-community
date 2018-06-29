#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "ScrubberItemView.h"
#import "JTypes.h"
#import "Utils.h"

static const NSUserInterfaceItemIdentifier g_scrubberItemIdentifier = @"scrubberItem";
static const int g_heightOfTouchBar = 30;
static const int g_interItemSpacings = 5;

@interface ScrubberItem : NSObject
@property (retain, nonatomic) NSString * text;
@property (retain, nonatomic) NSImage * img;
@property (nonatomic) execute jaction;
@end

@implementation ScrubberItem
@end

@interface NSScrubberContainer : NSCustomTouchBarItem<NSScrubberDataSource, NSScrubberDelegate, NSScrubberFlowLayoutDelegate>
{
    ScrubberItemView * _lastSelected;
}
@property (retain, nonatomic) NSMutableArray * items;
@end

static NSMutableArray * _convertItems(ScrubberItemData * items, int count) {
    NSMutableArray * nsarray = [[[NSMutableArray alloc] initWithCapacity:count] autorelease];
    for (int c = 0; c < count; ++c) {
        ScrubberItem * si = [[[ScrubberItem alloc] init] autorelease];
        si.text = createString(&items[c]);
        si.img = createImg(&items[c]);
        si.jaction = items[c].action;
        [nsarray addObject:si];
    }
    return nsarray;
}

@implementation NSScrubberContainer

- (NSInteger)numberOfItemsForScrubber:(NSScrubber *)scrubber {
    // NOTE: called from AppKit
    nstrace(@"scrubber [%@]: items count %d", self.identifier, self.items.count);
    return self.items.count;
}

- (NSScrubberItemView *)scrubber:(NSScrubber *)scrubber viewForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
    nstrace(@"scrubber [%@]: create viewForItemAtIndex %d", self.identifier, itemIndex);
    ScrubberItemView *itemView = [scrubber makeItemWithIdentifier:g_scrubberItemIdentifier owner:nil];

    ScrubberItem * itemData = [self.items objectAtIndex:itemIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: null item-data at index %d", self.identifier, itemIndex);
        return nil;
    }

    [itemView setImgAndText:itemData.img text:itemData.text];
    return itemView;
}

- (NSSize)scrubber:(NSScrubber *)scrubber layout:(NSScrubberFlowLayout *)layout sizeForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
    ScrubberItem * itemData = [self.items objectAtIndex:itemIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: null item-data at index %d", self.identifier, itemIndex);
        return NSMakeSize(0, 0);
    }

    NSFont * font = [NSFont systemFontOfSize:0]; // Specify a system font size of 0 to automatically use the appropriate size.
    const int imgW = itemData.img != nil ? itemData.img.size.width : 0;
    NSSize txtSize = itemData.text != nil ? [itemData.text sizeWithAttributes:@{ NSFontAttributeName:font }] : NSMakeSize(0, 0);

    CGFloat width = txtSize.width + imgW + 2*g_marginBorders + g_marginImgText + 13/*empiric diff for textfield paddings*/; // TODO: get rid of empiric, use size obtained from NSTextField
    nstrace(@"scrubber [%@]: sizeForItemAtIndex %d: txt='%@', iconW=%d, txt size = %1.2f, %1.2f, result width = %1.2f, font = %@", self.identifier, itemIndex, itemData.text, imgW, txtSize.width, txtSize.height, width, font);
    return NSMakeSize(width, g_heightOfTouchBar);
}

- (void)scrubber:(NSScrubber *)scrubber didSelectItemAtIndex:(NSInteger)selectedIndex {
    ScrubberItem * itemData = [self.items objectAtIndex:selectedIndex];
    if (itemData == nil) {
        nserror(@"scrubber [%@]: called didSelectItemAtIndex %d, but item-data at this index is null", self.identifier, selectedIndex);
        return;
    }

    NSScrubberItemView * view = [scrubber itemViewForItemAtIndex:selectedIndex];
    if (view != nil) {
        if (_lastSelected != nil)
            [_lastSelected setBackgroundSelected:false];

        _lastSelected = (ScrubberItemView *) view;
        [_lastSelected setBackgroundSelected:true];
    }

    nstrace(@"scrubber [%@]: perform action of scrubber item at %d", self.identifier, selectedIndex);
    (*itemData.jaction)();
}

@end

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
id createScrubber(const char* uid, int itemWidth, ScrubberItemData * items, int count) {
    NSString * nsid = [NSString stringWithUTF8String:uid];
    nstrace(@"create scrubber [%@] (thread: %@)", nsid, [NSThread currentThread]);

    NSScrubberContainer * scrubberItem = [[NSScrubberContainer alloc] initWithIdentifier:nsid]; // create non-autorelease object to be owned by java-wrapper
    scrubberItem.items = _convertItems(items, count);

    NSScrubber *scrubber = [[[NSScrubber alloc] initWithFrame:NSMakeRect(0, 0, itemWidth, g_heightOfTouchBar)] autorelease];

    scrubber.delegate = scrubberItem;
    scrubber.dataSource = scrubberItem;

    [scrubber registerClass:[ScrubberItemView class] forItemIdentifier:g_scrubberItemIdentifier];

    scrubber.showsAdditionalContentIndicators = NO;// For the image scrubber, we want the control to draw a fade effect to indicate that there is additional unscrolled content.
    scrubber.selectedIndex = -1;

    NSScrubberFlowLayout *scrubberLayout = [[[NSScrubberFlowLayout alloc] init] autorelease];
    scrubberLayout.itemSpacing = g_interItemSpacings;
    [scrubberLayout invalidateLayout];
    scrubber.scrubberLayout = scrubberLayout;

    scrubber.mode = NSScrubberModeFree;
    scrubber.showsArrowButtons = NO;
    scrubber.selectionOverlayStyle = nil;
    [scrubber.widthAnchor constraintGreaterThanOrEqualToConstant:itemWidth].active = YES;

    scrubberItem.view = scrubber;
    return scrubberItem;
}

void updateScrubber(id scrubObj, int itemWidth, ScrubberItemData * items, int count) {
    // NOTE: called from EDT (when update UI)
    NSScrubberContainer * container = scrubObj; // TODO: check types
    nstrace(@"async update scrubber [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
    NSAutoreleasePool * edtPool = [[NSAutoreleasePool alloc] init];
    NSMutableArray * nsitems = _convertItems(items, count);
    dispatch_async(dispatch_get_main_queue(), ^{
        container.items = nsitems;
        NSScrubber * scrubber = container.view;     // TODO: check types
//        nstrace(@"\tinvalidate layout of scrubber [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
        [scrubber.scrubberLayout invalidateLayout];
//        nstrace(@"\treload scrubber [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
        [scrubber reloadData];
    });
    [edtPool release];
}

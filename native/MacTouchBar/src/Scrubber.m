#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "ScrubberItemView.h"
#import "JTypes.h"
#import "Utils.h"

static const NSUserInterfaceItemIdentifier g_scrubberItemIdentifier = @"scrubberItem";
static const int g_heightOfTouchBar = 30;
static const int g_interItemSpacings = 5;

@interface NSScrubberContainer : NSCustomTouchBarItem<NSScrubberDataSource, NSScrubberDelegate, NSScrubberFlowLayoutDelegate>
@property (nonatomic) requestScrubberItem jsource;
@property (nonatomic) getScrubberItemsCount jcount;
@property (nonatomic) executeAt jactions;
@end

@implementation NSScrubberContainer

- (NSInteger)numberOfItemsForScrubber:(NSScrubber *)scrubber {
    // NOTE: called from AppKit
    const int result = self.jcount ? (*self.jcount)() : 0;
    nstrace(@"scrubber [%@]: items count %d", self.identifier, result);
    return result;
}

- (NSScrubberItemView *)scrubber:(NSScrubber *)scrubber viewForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit
    if (!self.jsource) {
        nserror(@"scrubber [%@]: called viewForItemAtIndex %d but scrubber hasn't items source", self.identifier, itemIndex);
        return nil;
    }

    nstrace(@"scrubber [%@]: create viewForItemAtIndex %d", self.identifier, itemIndex);
    ScrubberItemView *itemView = [scrubber makeItemWithIdentifier:g_scrubberItemIdentifier owner:nil];

    ScrubberItemData itemData;
    const int result = (*self.jsource)((int)itemIndex, &itemData);
    if (result != 0) {
        nserror(@"scrubber [%@]: can't obtain item-data at index %d", self.identifier, itemIndex);
        return nil;
    }

    [itemView setImgAndText:getImg(&itemData) text:getText(&itemData)];
    return itemView;
}

- (NSSize)scrubber:(NSScrubber *)scrubber layout:(NSScrubberFlowLayout *)layout sizeForItemAtIndex:(NSInteger)itemIndex {
    // NOTE: called from AppKit (when update layout)
    if (!self.jsource) {
        nserror(@"scrubber [%@]: called sizeForItemAtIndex %d but scrubber hasn't items source", self.identifier, itemIndex);
        return NSMakeSize(0, 0);
    }

    ScrubberItemData itemData;
    const int result = (*self.jsource)((int)itemIndex, &itemData);
    if (result != 0) {
        nserror(@"scrubber [%@]: can't obtain item-data at index %d", self.identifier, itemIndex);
        return NSMakeSize(0, 0);
    }

    NSString * text = getText(&itemData);
    const int imgW = itemData.rasterW > 0 ? itemData.rasterW : 0;

    NSFont * font = [NSFont systemFontOfSize:0]; // Specify a system font size of 0 to automatically use the appropriate size.
    NSSize txtSize = [text sizeWithAttributes:@{ NSFontAttributeName:font }];
    CGFloat width = txtSize.width + imgW + 2*g_marginBorders + g_marginImgText + 13/*empiric diff for textfield paddings*/; // TODO: get rid of empiric, use size obtained from NSTextField

    nstrace(@"scrubber [%@]: sizeForItemAtIndex %d: txt='%@', iconW=%d, txt size = %1.2f, %1.2f, result width = %1.2f", self.identifier, itemIndex, text, imgW, txtSize.width, txtSize.height, width);
    return NSMakeSize(width, g_heightOfTouchBar);
}

- (void)scrubber:(NSScrubber *)scrubber didSelectItemAtIndex:(NSInteger)selectedIndex {
    if (!self.jactions) {
        nserror(@"scrubber [%@]: called didSelectItemAtIndex %d but scrubber hasn't actions callback", self.identifier, selectedIndex);
        return;
    }

    nstrace(@"scrubber [%@]: perform action of scrubber item at %d", self.identifier, selectedIndex);
    (*self.jactions)((int)selectedIndex);
}

@end

// NOTE: called from AppKit (when TB becomes visible)
id createScrubber(const char* uid, int itemWidth, requestScrubberItem jsource, getScrubberItemsCount jcount, executeAt jactions) {
    NSString * nsid = [NSString stringWithUTF8String:uid];
    nstrace(@"create scrubber [%@]", nsid);

    NSScrubberContainer * scrubberItem = [[NSScrubberContainer alloc] initWithIdentifier:nsid]; // create non-autorelease object to be owned by java-wrapper
    scrubberItem.jsource = jsource;
    scrubberItem.jcount = jcount;
    scrubberItem.jactions = jactions;

    NSScrubber *scrubber = [[[NSScrubber alloc] initWithFrame:NSMakeRect(0, 0, itemWidth, g_heightOfTouchBar)] autorelease];

    scrubber.delegate = scrubberItem;
    scrubber.dataSource = scrubberItem;

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
    [scrubber.widthAnchor constraintGreaterThanOrEqualToConstant:itemWidth].active = YES;

    scrubberItem.view = scrubber;
    return scrubberItem;
}

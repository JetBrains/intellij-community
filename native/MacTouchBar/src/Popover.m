#import <Foundation/Foundation.h>
#import "TouchBar.h"
#import "Utils.h"

static void _setPopoverData(NSPopoverTouchBarItem * popoverItem, int itemWidth, NSString * nstext, NSImage * img, id tbObjExpand, id tbObjTapAndHold) {
    popoverItem.collapsedRepresentationImage = img;
    popoverItem.collapsedRepresentationLabel = nstext;
    if (itemWidth > 0) // Otherwise: create 'flexible' view
        [popoverItem.collapsedRepresentation.widthAnchor constraintEqualToConstant:itemWidth].active = YES;

    popoverItem.showsCloseButton = YES;
    popoverItem.popoverTouchBar = ((TouchBar*)tbObjExpand).touchBar;
    popoverItem.pressAndHoldTouchBar = ((TouchBar*)tbObjTapAndHold).touchBar;
}

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
__used
NS_RETURNS_RETAINED
id createPopover(const char * uid, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSString * nsUid = createStringFromUTF8(uid);
    nstrace(@"create popover [%@] (thread: %@)", nsUid, [NSThread currentThread]);

    NSPopoverTouchBarItem * popoverItem = [[NSPopoverTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = createStringFromUTF8(text);
    _setPopoverData(popoverItem, itemWidth, nstext, img, tbObjExpand, tbObjTapAndHold);
    return popoverItem;
}

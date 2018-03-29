#import <Foundation/Foundation.h>
#import "TouchBar.h"
#import "Utils.h"

static void _setPopoverData(NSPopoverTouchBarItem * popoverItem, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = getString(text);

    popoverItem.collapsedRepresentationImage = img;
    popoverItem.collapsedRepresentationLabel = nstext;
    if (itemWidth > 0) // Otherwise: create 'flexible' view
        [popoverItem.collapsedRepresentation.widthAnchor constraintEqualToConstant:itemWidth].active = YES;

    popoverItem.showsCloseButton = YES;
    popoverItem.popoverTouchBar = ((TouchBar*)tbObjExpand).touchBar;
    popoverItem.pressAndHoldTouchBar = ((TouchBar*)tbObjTapAndHold).touchBar;
}

// NOTE: called from AppKit (creation when TB becomes visible)
id createPopover(const char * uid, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSString * nsUid = getString(uid);
    nstrace(@"create popover [%@]", nsUid);

    NSPopoverTouchBarItem * popoverItem = [[NSPopoverTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    _setPopoverData(popoverItem, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold);
    return popoverItem;
}

// NOTE: called from EDT (when update UI)
void updatePopover(id popoverObj, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSPopoverTouchBarItem * popoverItem = popoverObj; // TODO: check types
    nstrace(@"update popover [%@]", popoverItem.identifier);

    _setPopoverData(popoverItem, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold);
}

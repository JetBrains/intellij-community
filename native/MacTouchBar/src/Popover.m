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
id createPopover(const char * uid, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSString * nsUid = createStringFromUTF8(uid);
    nstrace(@"create popover [%@] (thread: %@)", nsUid, [NSThread currentThread]);

    NSPopoverTouchBarItem * popoverItem = [[NSPopoverTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = createStringFromUTF8(text);
    _setPopoverData(popoverItem, itemWidth, nstext, img, tbObjExpand, tbObjTapAndHold);
    return popoverItem;
}

// NOTE: called from EDT (when update UI)
void updatePopover(id popoverObj, int itemWidth, const char * text, const char * raster4ByteRGBA, int w, int h, id tbObjExpand, id tbObjTapAndHold) {
    NSPopoverTouchBarItem * popoverItem = popoverObj; // TODO: check types
    nstrace(@"async update popover [%@] (thread: %@)", popoverItem.identifier, [NSThread currentThread]);
    NSAutoreleasePool * edtPool = [[NSAutoreleasePool alloc] init];
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = createStringFromUTF8(text);
    dispatch_async(dispatch_get_main_queue(), ^{
        // NOTE: block is copied, img/text objects is automatically retained
//        nstrace(@"\tperform update popover [%@] (thread: %@)", popoverItem.identifier, [NSThread currentThread]);
        _setPopoverData(popoverItem, itemWidth, nstext, img, tbObjExpand, tbObjTapAndHold);
    });
    [edtPool release];
}

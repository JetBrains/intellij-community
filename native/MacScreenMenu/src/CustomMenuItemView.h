#ifndef MACSCREENMENU_CUSTOMMENUITEMVIEW_H
#define MACSCREENMENU_CUSTOMMENUITEMVIEW_H

#import <AppKit/AppKit.h>

@class MenuItem;
@interface CustomMenuItemView : NSView {
    int16_t fireTimes;
    BOOL isSelected;
    NSSize shortcutSize;
    NSSize textSize;
    NSTrackingArea * trackingArea;
    @public MenuItem * owner;
}

@property (retain) NSString * keyShortcut;

- (id)initWithOwner:(MenuItem *)menuItem;
- (void)recalcSizes;
- (void)setSelected:(BOOL)selected;
@end

#endif //MACSCREENMENU_CUSTOMMENUITEMVIEW_H

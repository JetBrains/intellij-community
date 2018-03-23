#import "PopoverDesc.h"
#import "TouchBar.h"
#import "Utils.h"

@interface PopoverDesc() {
    int _width;
}
@property (retain, nonatomic) NSImage * img;
@property (retain, nonatomic) NSString * text;
@property (retain, nonatomic) TouchBar * expandBar;
@property (retain, nonatomic) TouchBar * tapHoldBar;
@end

@implementation PopoverDesc
- (id)init:(NSString *)text img:(NSImage *)img popoverWidth:(int)popW {
    self = [super init];
    if (self) {
        self.img = img;
        self.text = text;
        _width = popW;
    }
    return self;
}

- (nullable __kindof NSTouchBarItem *)create {
    nstrace(@"create popover [%@]", self.uid);
    NSPopoverTouchBarItem * popoverTouchBarItem = [[[NSPopoverTouchBarItem alloc] initWithIdentifier:self.uid] autorelease];
    popoverTouchBarItem.showsCloseButton = YES;

    if (_width <= 0) {
        // create 'flexible' view
        if (self.img != nil)
            popoverTouchBarItem.collapsedRepresentationImage = self.img;
        if (self.text != nil)
            popoverTouchBarItem.collapsedRepresentationLabel = self.text;
    } else {
        // create fixed width view
        NSButton *button = [[[NSButton alloc] init] autorelease];
        [button setImagePosition:NSImageLeft];
        [button setImage:self.img];
        [button setTitle:self.text];
        [button setTarget:popoverTouchBarItem];
        [button setAction:@selector(showPopover:)];
        [button setBezelStyle:NSRoundedBezelStyle];
        [button.widthAnchor constraintEqualToConstant:_width].active = YES;

        popoverTouchBarItem.collapsedRepresentation = button; // strong
    }

    popoverTouchBarItem.popoverTouchBar = self.expandBar.touchBar;
    popoverTouchBarItem.pressAndHoldTouchBar = self.tapHoldBar.touchBar;
    return popoverTouchBarItem;
}

@synthesize img, text, expandBar, tapHoldBar;
@end

void setPopoverExpandTouchBar(id popoverobj, id expandTB) {
    PopoverDesc * pdesc = (PopoverDesc *) popoverobj;
    pdesc.expandBar = expandTB;
    nstrace(@"set expandTB to popover [%@], text='%@', tb='%@'", pdesc.uid, pdesc.text, pdesc.expandBar);
}

void setPopoverTapAndHoldTouchBar(id popoverobj, id tapHoldTB) {
    PopoverDesc * pdesc = (PopoverDesc *) popoverobj;
    pdesc.tapHoldBar = tapHoldTB;
    nstrace(@"set tapHoldTB to popover [%@], text='%@', tb='%@'", pdesc.uid, pdesc.text, pdesc.tapHoldBar);
}

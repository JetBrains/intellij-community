#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "JTypes.h"
#import "Utils.h"

const NSSize g_defaultMinSize = {72, 30}; // empiric value

@interface NSButtonJAction : NSButton
@property (nonatomic) NSButtonType btype;
@property (nonatomic) CGFloat bwidth;
@property (nonatomic) execute jaction;
- (id)init;
- (void)doAction;
+ (Class)cellClass;
@end

@interface NSButtonCellEx : NSButtonCell
@property (nonatomic) CGFloat myBorder;
@property (nonatomic) CGFloat myMargin;
@end

@implementation NSButtonJAction
- (id)init {
    self = [super init];
    if (self) {
        self.btype = NSButtonTypeMomentaryLight;
        self.bwidth = 0;
        NSCell * cell = [self cell];
        [cell setLineBreakMode:NSLineBreakByTruncatingTail];
        [self setBezelStyle:NSRoundedBezelStyle];
        [self setMargins:2 border:5];
        // NSLog(@"created button [%@]: cell-class=%@", self, [[self cell] className]);
    }
    return self;
}
- (void)setMargins:(int)margin border:(int)border {
    NSCell * cell = [self cell];
    if ([cell isKindOfClass:[NSButtonCellEx class]]) {
        NSButtonCellEx * cellEx = cell;
        cellEx.myBorder = border;
        cellEx.myMargin = margin;
    } else
        nserror(@"setMargin mustn't be called because internal cell isn't kind of NSButtonCellEx");
}
- (void)doAction {
    if (self.jaction) {
        nstrace(@"button [%@]: doAction", self);
        (*self.jaction)();
    } else
        nstrace(@"button [%@]: empty action, nothing to execute", self);
}
+ (Class)cellClass
{
    return [NSButtonCellEx class];
}
@end

@implementation NSButtonCellEx
// Uncomment for visual debug
//
//-(void)drawBezelWithFrame:(NSRect)frame inView:(NSView *)controlView
//{
//    NSLog(@"drawBezelWithFrame: %@", NSStringFromRect(frame));
//    [super drawBezelWithFrame:frame inView:controlView];
//
//    [[NSGraphicsContext currentContext] saveGraphicsState];
//
//    CGFloat dashPattern[] = {6,4}; //make your pattern here
//    NSBezierPath *textViewSurround = [NSBezierPath bezierPathWithRoundedRect:frame xRadius:10 yRadius:10];
//    [textViewSurround setLineWidth:1.0f];
//    [textViewSurround setLineDash:dashPattern count:2 phase:0];
//    [[NSColor colorWithRed:105/255.0 green:211/255.0 blue:232/255.0 alpha:1.0] set];
//    [textViewSurround stroke];
//
//    [NSGraphicsContext restoreGraphicsState];
//}
- (NSSize)cellSizeForBounds:(NSRect)rect {
    if (self.title == nil) {
        // NSLog(@"\t empty text, use default size %@", NSStringFromSize(g_defaultMinSize));
        return g_defaultMinSize;
    }
    const NSSize txtSize = [self.title sizeWithAttributes:@{ NSFontAttributeName:self.font }];
    const CGFloat imgW = self.image == nil ? 0 : self.image.size.width;
    NSSize result = g_defaultMinSize;
    result.width = txtSize.width + imgW + 2*_myBorder + _myMargin;
    if (result.width < g_defaultMinSize.width)
        result.width = g_defaultMinSize.width;
    // NSLog(@"\t text='%@', text-size = %@, result = %@", self.title, NSStringFromSize(txtSize), NSStringFromSize(result));
    return result;
}
- (void)drawInteriorWithFrame:(NSRect)frame inView:(NSView *)controlView {
//    NSLog(@"drawInteriorWithFrame: %@", NSStringFromRect(frame));
    if (self.image == nil) {
        [super drawTitle:self.attributedTitle withFrame:frame inView:controlView];
        return;
    }

    if (self.title == nil) {
        [super drawImage:self.image withFrame:frame inView:controlView];
        return;
    }

    const CGFloat imgW = self.image.size.width;
    NSSize txtSize = [self.title sizeWithAttributes:@{ NSFontAttributeName:self.font }];
    const CGFloat txtW = txtSize.width;
    const CGFloat fullW = self.myBorder*2 + txtW + self.myMargin + imgW;
    NSRect rcImg = frame;
    NSRect rcTxt = frame;
    if (fullW <= frame.size.width) {
        const CGFloat delta = frame.size.width - fullW;
        rcImg.origin.x = delta/2 + self.myBorder;
        rcImg.size.width = imgW;
        rcTxt.origin.x = rcImg.origin.x + imgW + self.myMargin;
        rcTxt.size.width = txtW + self.myBorder;
    } else {
        rcImg.origin.x = self.myBorder;
        rcImg.size.width = imgW;
        rcTxt.origin.x = rcImg.origin.x + imgW + self.myMargin;
        rcTxt.size.width = frame.size.width - self.myBorder - rcTxt.origin.x;
    }

    if (rcImg.size.width > 0)
        [super drawImage:self.image withFrame:rcImg inView:controlView];
    if (rcTxt.size.width > 0)
        [super drawTitle:self.attributedTitle withFrame:rcTxt inView:controlView];
}
@end

const int BUTTON_UPDATE_WIDTH   = 1;
const int BUTTON_UPDATE_FLAGS   = 1 << 1;
const int BUTTON_UPDATE_TEXT    = 1 << 2;
const int BUTTON_UPDATE_IMG     = 1 << 3;
const int BUTTON_UPDATE_ACTION  = 1 << 4;
const int BUTTON_UPDATE_ALL     = ~0;

const int BUTTON_FLAG_DISABLED  = 1;
const int BUTTON_FLAG_SELECTED  = 1 << 1;
const int BUTTON_FLAG_COLORED   = 1 << 2;
const int BUTTON_FLAG_TOGGLE    = 1 << 3;

const int BUTTON_PRIORITY_SHIFT     = 3*8;
const unsigned int BUTTON_PRIORITY_MASK      = 0xFF << BUTTON_PRIORITY_SHIFT;

static int _getPriority(int flags) {
    return (flags & BUTTON_PRIORITY_MASK) >> BUTTON_PRIORITY_SHIFT;
}

static void _setButtonData(NSButtonJAction *button, int updateOptions, int buttonWidth, int buttonFlags, NSString *nstext, NSImage *img, execute jaction) {
    if (updateOptions & BUTTON_UPDATE_ACTION) {
        button.jaction = jaction;
        if (jaction) {
            [button setTarget:button];
            [button setAction:@selector(doAction)];
            [button setEnabled:YES];
        } else {
            [button setTarget:nil];
            [button setAction:NULL];
            [button setEnabled:NO];
        }
    }

    if (updateOptions & BUTTON_UPDATE_TEXT) {
        if (nstext != nil) {
            [button setFont:[NSFont systemFontOfSize:0]];
        }
        [button setTitle:nstext];
    }

    if (updateOptions & BUTTON_UPDATE_IMG)
        [button setImage:img];

    if (updateOptions & BUTTON_UPDATE_WIDTH) {
        button.bwidth = buttonWidth;
        if (button.bwidth > 0.1f)
            [button.widthAnchor constraintEqualToConstant:button.bwidth].active = YES;
        else
            [button.widthAnchor constraintEqualToAnchor:button.widthAnchor].active = NO;
    }

    if (updateOptions & BUTTON_UPDATE_FLAGS) {
        const bool toggle = (buttonFlags & BUTTON_FLAG_TOGGLE) != 0;
        const enum NSButtonType btype = toggle ? NSButtonTypePushOnPushOff : NSButtonTypeMomentaryLight;
        if (btype != button.btype) {
            [button setButtonType:btype];
            if (toggle) {
                button.bezelColor = NSColor.selectedControlColor;
            } else {
                button.bezelColor = NSColor.controlColor;
            }
        }

        if (buttonFlags & BUTTON_FLAG_COLORED) {
            button.bezelColor = [NSColor colorWithRed:0 green:130/255.f blue:215/255.f alpha:1];
        } else if (buttonFlags & BUTTON_FLAG_SELECTED) {
            if (toggle) {
                button.state = NSOnState;
            } else {
                button.bezelColor = NSColor.selectedControlColor;
            }
        } else {
            if (toggle) {
                button.state = NSOffState;
            } else {
                button.bezelColor = NSColor.controlColor;
            }
        }

        const bool enabled = (buttonFlags & BUTTON_FLAG_DISABLED) == 0;
        if (enabled != button.enabled) {
            [button setEnabled:enabled];
        }
    }

    if (button.image != nil) {
        if (button.title != nil && button.title.length > 0)
            [button setImagePosition:NSImageLeft];
        else
            [button setImagePosition:NSImageOnly];
    } else {
        [button setImagePosition:NSNoImage];
    }
}

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
id createButton(
        const char *uid,
        int buttonWidth,
        int buttonFlags,
        const char *text,
        const char *raster4ByteRGBA, int w, int h,
        execute jaction
) {
    NSString *nsUid = createStringFromUTF8(uid);
    // NSLog(@"create button [%@] (thread: %@)", nsUid, [NSThread currentThread]);

    NSCustomTouchBarItem *customItemForButton = [[NSCustomTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSButtonJAction *button = [[[NSButtonJAction alloc] init] autorelease];

    NSImage *img = createImgFrom4ByteRGBA((const unsigned char *) raster4ByteRGBA, w, h);
    NSString *nstext = createStringFromUTF8(text);
    _setButtonData(button, BUTTON_UPDATE_ALL, buttonWidth, buttonFlags, nstext, img, jaction);
    customItemForButton.view = button; // NOTE: view is strong

    const int prio = _getPriority(buttonFlags);
    if (prio != 0) {
        // empiric: when prio <= -1.f then item won't be shown when at least 1 item with 0-priority (normal, default) presented => use interval from -0.5 to 0.5
        customItemForButton.visibilityPriority = (float)(prio - 127)/256;
        // NSLog(@"item [%@], prio=%1.5f", nsUid, customItemForButton.visibilityPriority);
    }
    return customItemForButton;
}

// NOTE: called from EDT (when update UI)
void updateButton(
        id buttObj,
        int updateOptions,
        int buttonWidth,
        int buttonFlags,
        const char *text,
        const char *raster4ByteRGBA, int w, int h,
        execute jaction
) {
    NSCustomTouchBarItem *container = buttObj; // TODO: check types
    NSButtonJAction *button = (container).view; // TODO: check types

    NSAutoreleasePool *edtPool = [NSAutoreleasePool new];
    NSImage *img = createImgFrom4ByteRGBA((const unsigned char *) raster4ByteRGBA, w, h);
    NSString *nstext = createStringFromUTF8(text);

    if ([NSThread isMainThread]) {
        nstrace(@"sync update button [%@] (main thread: %@)", container.identifier, [NSThread currentThread]);
        _setButtonData(button, updateOptions, buttonWidth, buttonFlags, nstext, img, jaction);
    } else {
        nstrace(@"async update button [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
        dispatch_async(dispatch_get_main_queue(), ^{
            // NOTE: block is copied, img/text objects is automatically retained
            // nstrace(@"\tperform update button [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
            _setButtonData(button, updateOptions, buttonWidth, buttonFlags, nstext, img, jaction);
        });
    }

    [edtPool release];
}

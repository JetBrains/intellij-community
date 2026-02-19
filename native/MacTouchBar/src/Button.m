#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "JTypes.h"
#import "Utils.h"

const NSSize g_defaultMinSize = {72, 30}; // empiric value

@interface NSButtonJAction : NSButton
@property (nonatomic) NSButtonType btype;
@property (nonatomic) CGFloat bwidth;
@property (nonatomic) execute jaction;
@property (nonatomic, assign) NSString * uid; // for debug only
@property (nonatomic, retain) NSLayoutConstraint * constraintEqualToConstant;
@property (nonatomic, retain) NSLayoutConstraint * constraintLessThanOrEqualToConstant;
@property (nonatomic, retain) NSLayoutConstraint * constraintGreaterThanOrEqualToConstant;
- (id)init;
- (void)doAction;
+ (Class)cellClass;
@end

@interface NSButtonCellEx : NSButtonCell
@property (nonatomic) CGFloat myBorder;
@property (nonatomic) CGFloat myMargin;
@property (atomic, retain) NSImage * myArrowImg;

@property (retain) NSAttributedString * myText;
@property (retain) NSAttributedString * myHint;
@end

@implementation NSButtonJAction
- (id)init {
    self = [super init];
    if (self) {
        self.btype = NSButtonTypeMomentaryLight;
        self.bwidth = 0;
        self.constraintEqualToConstant = nil; // NOTE: we must remember constraints to be able to toggle them
        self.constraintLessThanOrEqualToConstant = nil;
        self.constraintGreaterThanOrEqualToConstant = nil;

        NSCell * cell = [self cell];
        [cell setLineBreakMode:NSLineBreakByTruncatingTail];
        [self setBezelStyle:NSBezelStyleRounded];
        [self setMargins:3 border:8];

        if ([cell isKindOfClass:[NSButtonCellEx class]]) {
            NSButtonCellEx * cellEx = (NSButtonCellEx * )cell;
            cellEx.myText = nil;
            cellEx.myHint = nil;
        }
        // NSLog(@"created button [%@]: cell-class=%@", self, [[self cell] className]);
    }
    return self;
}
- (void)setMargins:(int)margin border:(int)border {
    NSCell * cell = [self cell];
    if ([cell isKindOfClass:[NSButtonCellEx class]]) {
        NSButtonCellEx * cellEx = (NSButtonCellEx *)cell;
        cellEx.myBorder = border;
        cellEx.myMargin = margin;
    } else
        nserror(@"setMargin mustn't be called because internal cell isn't kind of NSButtonCellEx");
}
- (void)setArrowImg:(NSImage *)arrowImg {
    NSCell * cell = [self cell];
    if ([cell isKindOfClass:[NSButtonCellEx class]]) {
        NSButtonCellEx * cellEx = (NSButtonCellEx *)cell;
        cellEx.myArrowImg = arrowImg;
    } else
        nserror(@"setArrowImg mustn't be called because internal cell isn't kind of NSButtonCellEx");
}
- (void)setTextAndHint:(NSString *)text hint:(NSString *)hintText isHintDisabled:(int)isHintDisabled {
    NSCell * cell = [self cell];
    if ([cell isKindOfClass:[NSButtonCellEx class]]) {
        //NSLog(@"setTextAndHint: text=%@, hint=%@", text, hintText);
        NSButtonCellEx * cellEx = (NSButtonCellEx *)cell;
        const CGFloat textFontSize = 0; // system-default (12pt)
        const CGFloat hintFontSize = 9.f; // empiric
        cellEx.myText = text == nil || text.length <= 0 ? nil :
            [[NSAttributedString alloc] initWithString:text attributes:@{ NSFontAttributeName : [NSFont systemFontOfSize:textFontSize]}];
        if (hintText == nil || hintText.length <= 0) {
          cellEx.myHint = nil;
        } else {
          // NOTE: [NSColor disabledControlTextColor] is white
          NSColor * color = isHintDisabled != 0 ? [NSColor grayColor] : [NSColor controlTextColor];
          cellEx.myHint = [[NSAttributedString alloc] initWithString:hintText attributes:@{
                            NSForegroundColorAttributeName : color,
                            NSFontAttributeName : [NSFont systemFontOfSize:hintFontSize]
                          }];
        }

        [cellEx.myText release];
        [cellEx.myHint release];
    } else
        nserror(@"setTextAndHint mustn't be called because internal cell isn't kind of NSButtonCellEx");
}

- (void)disableAllConstraints {
    if (self.constraintEqualToConstant != nil) {
        self.constraintEqualToConstant.active = NO;
    }
    if (self.constraintLessThanOrEqualToConstant != nil) {
        self.constraintLessThanOrEqualToConstant.active = NO;
    }
    if (self.constraintGreaterThanOrEqualToConstant != nil) {
        self.constraintGreaterThanOrEqualToConstant.active = NO;
    }
}

- (void)doAction {
    if (self.jaction) {
        nstrace(@"button [%@]: doAction", self);
        (*self.jaction)();
    } else
        nstrace(@"button [%@]: empty action, nothing to execute", self);
}
// Uncomment for visual debug
//
//- (void) drawRect:(NSRect)dirtyRect {
//    [[NSGraphicsContext currentContext] saveGraphicsState];
//
////    NSColor* backgroundColor = [NSColor clearColor];
////    NSColor* backgroundColor = [NSColor yellowColor];
////    [backgroundColor setFill];
////    NSRectFill(dirtyRect);
//
//    [NSGraphicsContext restoreGraphicsState];
//
//    NSLog(@"drawRect [%@]: %@", self.uid, NSStringFromRect(dirtyRect));
//    [super drawRect:dirtyRect];
//}

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
//    NSLog(@"\tdrawBezelWithFrame: %@", NSStringFromRect(frame));
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
//- (void)drawDebugRect:(NSRect)frame color:(NSColor *)col {
//    // NSLog(@"drawDebugRect: %@", NSStringFromRect(frame));
//
//    [[NSGraphicsContext currentContext] saveGraphicsState];
//
//    if (col == nil)
//        col = [NSColor colorWithRed:105/255.0 green:211/255.0 blue:232/255.0 alpha:1.0];
//    [col set];
//    [col setStroke];
//    [col setFill];
//    NSFrameRect(frame);
//
//    [NSGraphicsContext restoreGraphicsState];
//}
- (NSSize)cellSizeForBounds:(NSRect)rect {
    if (self.myText == nil) {
        // NSLog(@"\t empty text, use default size %@", NSStringFromSize(g_defaultMinSize));
        return g_defaultMinSize;
    }
    const CGFloat txtSizeW = self.myText == nil ? 0 : [self.myText size].width;
    const CGFloat hintSizeW = self.myHint == nil ? 0 : [self.myHint size].width;
    const CGFloat textWidth = txtSizeW > hintSizeW ? txtSizeW : hintSizeW;
    const CGFloat imgW = self.myHint != nil || self.image == nil ? 0 : self.image.size.width;// don't draw image when hint presented
    NSSize result = g_defaultMinSize;
    result.width = textWidth + imgW + 2*_myBorder + _myMargin;
    if (result.width < g_defaultMinSize.width)
        result.width = g_defaultMinSize.width;
    //NSLog(@"\t text='%@' hint='%@', text-size = %f, result = %@", self.myText, self.myHint, txtSizeW, NSStringFromSize(result));
    return result;
}
- (void)drawInteriorWithFrame:(NSRect)frame inView:(NSView *)controlView {
    // NSLog(@"\tdrawInteriorWithFrame: %@", NSStringFromRect(frame));
    if (self.myText == nil) {
        [super drawImage:self.image withFrame:frame inView:controlView];
        return;
    }
    if (self.myHint == nil) {
        [self drawTextAndImage:frame inView:controlView];
    } else {
        [self drawTextWithHint:frame inView:controlView];
    }
}
- (void)drawTextAndImage:(NSRect)frame inView:(NSView *)controlView {
    const CGFloat imgW = self.image == nil ? 0 : self.image.size.width;
    const CGFloat arrowImgW = self.myArrowImg == nil ? 0 : self.myArrowImg.size.width;
    const CGFloat arrowMarginL = 2;
    const CGFloat arrowMarginR = 2;

    const CGFloat imgWithMargin = imgW > 0 ? self.myMargin + imgW : 0;
    const CGFloat arrowImgWithMargins = arrowImgW > 0 ? arrowMarginL + arrowImgW + arrowMarginR : 0;

    // NOTE: self.myText != nil here
    NSSize txtSize = [self.myText size];
    //NSLog(@"\t drawTextAndImage: text='%@' text-size = %@", self.myText, NSStringFromSize(txtSize));
    const CGFloat txtW = txtSize.width;
    const CGFloat borderL = self.myBorder;
    const CGFloat borderR = arrowImgW > 0 ? 2 : self.myBorder;
    const CGFloat fullContentW = borderL + imgWithMargin + txtW + borderR;
    const CGFloat availableWidth = frame.size.width - arrowImgWithMargins;
    NSRect rcImg = frame;
    rcImg.size.width = imgW;
    NSRect rcTxt = frame;
    if (fullContentW <= availableWidth) {
        const CGFloat delta = availableWidth - fullContentW;
        if (imgW > 0) {
            rcImg.origin.x = delta/2 + borderL;
            rcTxt.origin.x = rcImg.origin.x + imgWithMargin;
        } else {
            rcTxt.origin.x = delta/2 + borderL;
        }
        rcTxt.size.width = txtW + 1;
    } else {
        if (imgW > 0) {
            rcImg.origin.x = borderL;
            rcTxt.origin.x = rcImg.origin.x + imgWithMargin;
            rcTxt.size.width = availableWidth - borderR - rcTxt.origin.x;
        } else {
            rcTxt.origin.x = borderL;
            rcTxt.size.width = availableWidth - borderL - borderR;
        }
    }

    if (arrowImgW > 0) {
        NSRect rcArrow = frame;
        rcArrow.size.width = arrowImgW;
        rcArrow.origin.x = frame.size.width - arrowMarginR - arrowImgW;
        [super drawImage:self.myArrowImg withFrame:rcArrow inView:controlView];
    }

    if (imgW > 0) {
        [super drawImage:self.image withFrame:rcImg inView:controlView];
//        NSColor * col = [NSColor colorWithRed:105/255.0 green:211/255.0 blue:0/255.0 alpha:1.0];
//        [self drawDebugRect:rcImg color:col];
    }
    if (rcTxt.size.width > 0) {
        [super drawTitle:self.myText withFrame:rcTxt inView:controlView];

//      NSColor * col = [NSColor colorWithRed:105/255.0 green:0/255.0 blue:255/255.0 alpha:1.0];
//      [self drawDebugRect:rcTxt color:col];
    }
//  [self drawDebugRect:frame color:nil];
}
- (void)drawTextWithHint:(NSRect)frame inView:(NSView *)controlView {
    // NOTE: self.myText != nil and self.myHint != nil here
    NSSize txtSize = [self.myText size];
    NSSize hintSize = [self.myHint size];
    //NSLog(@"\t drawTextWithHint: text='%@' hint='%@', text-size = %@, hint-size = %@", self.myText, self.myHint, NSStringFromSize(txtSize), NSStringFromSize(hintSize));

    const CGFloat borderL = self.myBorder;
    const CGFloat borderR = self.myBorder;

    // calc text rect
    NSRect rcTxt = frame;
    rcTxt.size.height = frame.size.height/2;
    const CGFloat fullTextW = borderL + txtSize.width + borderR;
    if (fullTextW <= frame.size.width) {
        const CGFloat delta = frame.size.width - fullTextW;
        rcTxt.origin.x = delta/2 + borderL;
        rcTxt.size.width = txtSize.width + 1;
    } else {
        rcTxt.origin.x = borderL;
        rcTxt.size.width = frame.size.width - borderL - borderR;
    }

    // calc hint rect
    NSRect rcHint = frame;
    rcHint.origin.y = frame.origin.y + frame.size.height/2;
    rcHint.size.height = frame.size.height/2 - 1;
    const CGFloat fullHintW = borderL/2 + hintSize.width + borderR/2; // hint has smaller font so use half of border
    if (fullHintW <= frame.size.width) {
        const CGFloat delta = frame.size.width - fullHintW;
        rcHint.origin.x = delta/2 + borderL/2;
        rcHint.size.width = hintSize.width + 1;
    } else {
        rcHint.origin.x = borderL/2;
        rcHint.size.width = frame.size.width - borderL/2 - borderR/2;
    }

    if (rcTxt.size.width > 0) {
        [super drawTitle:self.myText withFrame:rcTxt inView:controlView];
        [super drawTitle:self.myHint withFrame:rcHint inView:controlView];

//         NSColor * col = [NSColor colorWithRed:105/255.0 green:0/255.0 blue:255/255.0 alpha:1.0];
//         [self drawDebugRect:rcTxt color:col];
    }
//  [self drawDebugRect:frame color:nil];
}
@end

const int BUTTON_UPDATE_LAYOUT   = 1;
const int BUTTON_UPDATE_FLAGS   = 1 << 1;
const int BUTTON_UPDATE_TEXT    = 1 << 2;
const int BUTTON_UPDATE_IMG     = 1 << 3;
const int BUTTON_UPDATE_ACTION  = 1 << 4;
const int BUTTON_UPDATE_ALL     = ~0;

const int BUTTON_FLAG_DISABLED  = 1;
const int BUTTON_FLAG_SELECTED  = 1 << 1;
const int BUTTON_FLAG_COLORED   = 1 << 2;
const int BUTTON_FLAG_TOGGLE    = 1 << 3;
const int BUTTON_FLAG_TRANSPARENT_BG = 1 << 4;

const unsigned int LAYOUT_WIDTH_MASK       = 0x0FFF;
const unsigned int LAYOUT_FLAG_MIN_WIDTH   = 1 << 15;
const unsigned int LAYOUT_FLAG_MAX_WIDTH   = 1 << 14;

const unsigned int LAYOUT_MARGIN_SHIFT     = 2*8;
const unsigned int LAYOUT_MARGIN_MASK      = 0xFF << LAYOUT_MARGIN_SHIFT;
const unsigned int LAYOUT_BORDER_SHIFT     = 3*8;
const unsigned int LAYOUT_BORDER_MASK      = 0xFF << LAYOUT_BORDER_SHIFT;

const int BUTTON_PRIORITY_SHIFT         = 3*8;
const unsigned int BUTTON_PRIORITY_MASK = 0xFF << BUTTON_PRIORITY_SHIFT;

static int _getPriority(int flags) {
    return (flags & BUTTON_PRIORITY_MASK) >> BUTTON_PRIORITY_SHIFT;
}

static int _getWidth(int layoutBits) {
    return (layoutBits & LAYOUT_WIDTH_MASK);
}

static int _getMargin(int layoutBits) {
    return (layoutBits & LAYOUT_MARGIN_MASK) >> LAYOUT_MARGIN_SHIFT;
}

static int _getBorder(int layoutBits) {
    return (layoutBits & LAYOUT_BORDER_MASK) >> LAYOUT_BORDER_SHIFT;
}

static void _setButtonData(NSButtonJAction *button, int updateOptions, int layoutBits, int buttonFlags,
                           NSString *nstext, NSString *nshint, int isHintDisabled, NSImage *img, execute jaction
) {
    // Suppress intermittent exceptions, for example:
    // from [NSView(NSConstraintBasedLayout) _tryToAddConstraint] with message
    // "Should translate autoresizing mask into constraints if _didChangeHostsAutolayoutEngineTo:YES."
    // Workaround for IDEA-272131 macOS: SIGILL at [libsystem_kernel] __kill
    @try {
        if (updateOptions & BUTTON_UPDATE_ACTION) {
            button.jaction = jaction;
            if (jaction) {
                [button setTarget:button];
                [button setAction:@selector(doAction)];
            } else {
                [button setTarget:nil];
                [button setAction:NULL];
            }
        }

        if (updateOptions & BUTTON_UPDATE_TEXT) {
            [button setTextAndHint:nstext hint:nshint isHintDisabled:isHintDisabled];
            button.needsDisplay = YES; // to induce update event (otherwise drawInteriorWithFrame with new text won't be invoked)
        }

        if (updateOptions & BUTTON_UPDATE_IMG)
            [button setImage:img];

        if (updateOptions & BUTTON_UPDATE_LAYOUT) {
            [button disableAllConstraints];
            const int width = _getWidth(layoutBits);
            button.bwidth = width;
            if (width > 0) {
                bool isMinWidth = (layoutBits & LAYOUT_FLAG_MIN_WIDTH) != 0;
                bool isMaxWidth = (layoutBits & LAYOUT_FLAG_MAX_WIDTH) != 0;
                if (isMinWidth && isMaxWidth) {
                    nserror(@"invalid arguments specified: both min and max bits are 1");
                }
                if (isMinWidth) {
                    //NSLog(@"set min width %d", width);
                    button.constraintGreaterThanOrEqualToConstant = [button.widthAnchor constraintGreaterThanOrEqualToConstant:button.bwidth];
                    button.constraintGreaterThanOrEqualToConstant.active = YES;
                } else if (isMaxWidth) {
                    //NSLog(@"set max width %d", width);
                    button.constraintLessThanOrEqualToConstant = [button.widthAnchor constraintLessThanOrEqualToConstant:button.bwidth];
                    button.constraintLessThanOrEqualToConstant.active = YES;
                } else {
                    //NSLog(@"set const width %d", width);
                    button.constraintEqualToConstant = [button.widthAnchor constraintEqualToConstant:button.bwidth];
                    button.constraintEqualToConstant.active = YES;
                }
            } else {
                // NSLog(@"set floating width");
                // everything is done inside [button disableAllConstraints];
            }

            { // process margins
                const int margin = _getMargin(layoutBits);
                const int border = _getBorder(layoutBits);
                if (margin > 0 || border > 0) {
                    [button setMargins:margin border:border];
                    //NSLog(@"set insets: m=%d b=%d", margin, border);
                }
            }
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
                    button.state = NSControlStateValueOn;
                } else {
                    button.bezelColor = NSColor.selectedControlColor;
                }
            } else {
                if (toggle) {
                    button.state = NSControlStateValueOff;
                } else {
                    button.bezelColor = NSColor.controlColor;
                }
            }

            const bool enabled = (buttonFlags & BUTTON_FLAG_DISABLED) == 0;
            if (enabled != button.enabled) {
                [button setEnabled:enabled];
            }

            if (buttonFlags & BUTTON_FLAG_TRANSPARENT_BG)
                [button setBordered:NO];
        }
    } @catch (NSException *ex) {
        NSLog(@"WARNING: suppressed exception from _setButtonData (workaround for IDEA-272131)");
    }
}

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
__used
NS_RETURNS_RETAINED
id createButton(
        const char *uid,
        int layoutBits,
        int buttonFlags,
        const char *text,
        const char *hint, int isHintDisabled,
        const char *raster4ByteRGBA, int w, int h,
        execute jaction
) {
    NSString *nsUid = createStringFromUTF8(uid);
    // NSLog(@"create button [%@] (thread: %@)", nsUid, [NSThread currentThread]);

    NSCustomTouchBarItem *customItemForButton = [[NSCustomTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSButtonJAction *button = [[[NSButtonJAction alloc] init] autorelease];
    button.uid = nsUid;

    NSImage *img = createImgFrom4ByteRGBA((const unsigned char *) raster4ByteRGBA, w, h);
    NSString *nstext = createStringFromUTF8(text);
    NSString *nshint = createStringFromUTF8(hint);
    _setButtonData(button, BUTTON_UPDATE_ALL, layoutBits, buttonFlags, nstext, nshint, isHintDisabled, img, jaction);
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
__used
void updateButton(
        id buttObj,
        int updateOptions,
        int layoutBits,
        int buttonFlags,
        const char *text,
        const char *hint, int isHintDisabled,
        const char *raster4ByteRGBA, int w, int h,
        execute jaction
) {
    @autoreleasepool {
      NSImage *img = createImgFrom4ByteRGBA((const unsigned char *) raster4ByteRGBA, w, h);
      NSString *nstext = createStringFromUTF8(text);
      NSString *nshint = createStringFromUTF8(hint);
      NSButtonJAction *button = ((NSCustomTouchBarItem *)buttObj).view;

      if ([NSThread isMainThread]) {
          NSButtonJAction *button = ((NSCustomTouchBarItem *)buttObj).view;
          // NSLog(@"sync update button [%@] (main thread: %@)", (NSCustomTouchBarItem *)buttObj.identifier, [NSThread currentThread]);
          _setButtonData(button, updateOptions, layoutBits, buttonFlags, nstext, nshint, isHintDisabled, img, jaction);
      } else {
          // NSLog(@"async update button [%@] (thread: %@)", (NSCustomTouchBarItem *)buttObj.identifier, [NSThread currentThread]);
          dispatch_async(dispatch_get_main_queue(), ^{
              // NOTE: block is copied, img/text objects is automatically retained
              // nstrace(@"\tperform update button [%@] (thread: %@)", (NSCustomTouchBarItem *)buttObj.identifier, [NSThread currentThread]);
              _setButtonData(button, updateOptions, layoutBits, buttonFlags, nstext, nshint, isHintDisabled, img, jaction);
          });
      }
    }
}

// NOTE: now is called from AppKit-thread (creation when TB becomes visible), but can be called from EDT (when update UI)
__used
void setArrowImage(id buttObj, const char *raster4ByteRGBA, int w, int h) {
    NSCustomTouchBarItem *container = buttObj;
    NSButtonJAction *button = (container).view;

    @autoreleasepool {
        NSImage *img = nil;
        if (raster4ByteRGBA != NULL && w > 0)
            img = createImgFrom4ByteRGBA((const unsigned char *) raster4ByteRGBA, w, h);

        if ([NSThread isMainThread]) {
            [button setArrowImg:img];
            //NSLog(@"sync set arrow: w=%d h=%d", w, h);
        } else {
            dispatch_async(dispatch_get_main_queue(), ^{
                // NOTE: block is copied, img/text objects is automatically retained
                // nstrace(@"\tperform update button [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
                [button setArrowImg:img];
                //NSLog(@"async set arrow: w=%d h=%d", w, h);
            });
        }
    }
}

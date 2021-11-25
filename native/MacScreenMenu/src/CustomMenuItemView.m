#include "CustomMenuItemView.h"
#include "Menu.h"

@implementation CustomMenuItemView

static CGFloat menuItemHeight = 18.f;
static CGFloat marginLeft = 20.f;
static CGFloat marginRight = 10.f;

static CGFloat gapTxtIcon = 5.f;
static CGFloat gapTxtShortcut = 23.f;

static NSFont * menuFont;
static NSFont * menuShortcutFont;

static NSColor * customBg = nil;

+ (void)initialize {
    menuFont = [NSFont menuBarFontOfSize:(0)];
    menuShortcutFont = [NSFont menuBarFontOfSize:(0)];

    NSDictionary * attributes = [NSDictionary dictionaryWithObjectsAndKeys:menuFont, NSFontAttributeName, nil];
    NSSize qSize = [[[[NSAttributedString alloc] initWithString:@"Q" attributes:attributes] autorelease] size];

    // use empiric proportions (to look like default view)
    menuItemHeight = qSize.height * 1.1f;
    marginLeft = menuItemHeight * 1.1f;
    marginRight = menuItemHeight * 0.55f;

    gapTxtIcon = menuItemHeight * 0.27f;
    gapTxtShortcut = menuItemHeight * 1.2f;

    // Initialize custom bg color (for light theme with enabled accessibility.reduceTransparency)
    // If we use transparent bg than we will see visual inconsistency
    // And it seems that we can't obtain this color from system
    NSUserDefaults * defs = [NSUserDefaults standardUserDefaults];
    NSDictionary<NSString *,id> * dict = [defs persistentDomainForName:@"com.apple.universalaccess.plist"];
    if (dict != nil) {
        id reduceVal = [dict valueForKey:@"reduceTransparency"];
        if (reduceVal != nil && [reduceVal isKindOfClass:[NSNumber class]] && [reduceVal intValue] != 0) {
            NSString * mode = [defs stringForKey:@"AppleInterfaceStyle"];
            if (mode == nil) { // light system theme
                customBg = [NSColor colorWithCalibratedWhite:246.f/255 alpha:1.f];
                [customBg retain];
                // NSLog(@"\treduceTransparency is enabled (use custom background color for menu items)");
            }
        }
    }

    // NSLog(@"\tmenuItemHeight=%1.2f, marginLeft=%1.2f, marginRight=%1.2f, gapTxtIcon=%1.2f, gapTxtShortcut=%1.2f",
    //      menuItemHeight, marginLeft, marginRight, gapTxtIcon, gapTxtShortcut);
}

- (id)initWithOwner:(MenuItem *)menuItem {
    NSRect viewRect = NSMakeRect(0, 0, /* width autoresizes */ 1, menuItemHeight);
    self = [super initWithFrame:viewRect];
    if (self == nil) {
        return self;
    }

    owner = menuItem;

    self.autoresizingMask = NSViewWidthSizable;
    self.keyShortcut = nil;

    fireTimes = 0;
    isSelected = NO;
    trackingArea = nil;
    shortcutSize = NSZeroSize;
    textSize = NSZeroSize;

    return self;
}

- (void)dealloc {
    if(trackingArea != nil) {
        [trackingArea release];
    }

    [super dealloc];
}

- (void)setSelected:(BOOL)selected {
    if (isSelected == selected) return;
    if (owner == nil || owner->nsMenuItem == nil) return;
    if (isSelected) {
        isSelected = NO;
    } else if (owner->nsMenuItem.enabled) {
        isSelected = YES;
    }

    [self setNeedsDisplay:YES];
}

- (void)mouseEntered:(NSEvent*)event {
    [self setSelected:YES];
}

- (void)mouseExited:(NSEvent *)event {
    [self setSelected:NO];
}

- (void)mouseUp:(NSEvent*)event {
    if (owner == nil || owner->nsMenuItem == nil) return;
    if (!(owner->nsMenuItem.enabled)) return;

    [self setSelected:!isSelected];

    fireTimes = 0;
    NSTimer *timer = [NSTimer timerWithTimeInterval:0.05 target:self selector:@selector(animateDismiss:) userInfo:nil repeats:YES];
    [[NSRunLoop currentRunLoop] addTimer:timer forMode:NSEventTrackingRunLoopMode];
}

- (void)keyDown:(NSEvent *)event {
    [[self nextResponder] keyDown:event];
    if ((event.keyCode == 36)  ||
        (event.keyCode == 76)  ||
        [[event characters] isEqualToString:@" "]
    )
        [self sendAction];
}

- (BOOL)acceptsFirstResponder {
    return YES;
}

-(void)updateTrackingAreas {
    if (owner == nil || owner->nsMenuItem == nil) return;
    [super updateTrackingAreas];
    if(trackingArea != nil) {
        [self removeTrackingArea:trackingArea];
        [trackingArea release];
    }

    int opts = (NSTrackingMouseEnteredAndExited | NSTrackingActiveAlways);
    trackingArea = [[NSTrackingArea alloc] initWithRect:[self bounds]
                                                options:opts
                                                  owner:self
                                               userInfo:nil];
    [self addTrackingArea:trackingArea];
}

-(void)animateDismiss:(NSTimer *)aTimer {
    if (fireTimes <= 2) {
        isSelected = !isSelected;
        [self setNeedsDisplay:YES];
    } else {
        [aTimer invalidate];
        [self sendAction];
    }

    fireTimes++;
}

- (void)sendAction {
    if (owner == nil || owner->nsMenuItem == nil) return;
    NSMenuItem * mi = owner->nsMenuItem;
    [NSApp sendAction:[mi action] to:[mi target] from:mi];

    NSMenu *menu = [mi menu];
    [menu cancelTracking];

    // NOTE: we can also invoke handler directly [owner handleAction:[owner menuItem]];
}

//#define VISUAL_DEBUG_CUSTOM_ITEM_VIEW

- (void) drawRect:(NSRect)dirtyRect {
    if (owner == nil || owner->nsMenuItem == nil) return;
    NSRect rectBounds = [self bounds];
    NSString * text = owner->nsMenuItem.title;

#ifdef VISUAL_DEBUG_CUSTOM_ITEM_VIEW
    [[NSColor yellowColor] set];
    NSFrameRectWithWidth([self bounds], 1.0f);
#endif // VISUAL_DEBUG_CUSTOM_ITEM_VIEW

    const BOOL isEnabled = owner->nsMenuItem.enabled;

    NSColor * textColor = [NSColor textColor];
    NSColor * bgColor = customBg != nil ? customBg : [NSColor clearColor];
    if (!isEnabled) {
        textColor = [NSColor grayColor];
    } else if (isSelected) {
        if (@available(macOS 10.14, *)) {
            bgColor = [NSColor controlAccentColor];
        } else {
            bgColor = [NSColor selectedControlColor];
        }
        textColor = [NSColor selectedMenuItemTextColor];
    }

    // 1. draw bg
    [bgColor set];
    NSRectFill(rectBounds);

    // 2. draw icon if presented
    CGFloat x = rectBounds.origin.x + marginLeft;
    NSImage * image = owner->nsMenuItem.image;
    if (image != nil) {
        NSRect imageBounds = rectBounds;
        imageBounds.origin.x = x;
        imageBounds.size.width = image.size.width;
        [image drawInRect:imageBounds];

        x += image.size.width + gapTxtIcon;
#ifdef VISUAL_DEBUG_CUSTOM_ITEM_VIEW
        [[NSColor redColor] set];
        NSFrameRectWithWidth(imageBounds, 1.0f);
#endif // VISUAL_DEBUG_CUSTOM_ITEM_VIEW
    }

    // 3. draw text
    [textColor set];
    NSDictionary *attributes = [NSDictionary dictionaryWithObjectsAndKeys:
            menuFont, NSFontAttributeName,
            textColor, NSForegroundColorAttributeName,
                    nil];
    NSRect txtBounds = rectBounds;
    txtBounds.origin.x = x;
    txtBounds.size.width = textSize.width;
    [text drawInRect:txtBounds withAttributes:attributes];
#ifdef VISUAL_DEBUG_CUSTOM_ITEM_VIEW
    [[NSColor blackColor] set];
    NSFrameRectWithWidth(txtBounds, 1.0f);
#endif // VISUAL_DEBUG_CUSTOM_ITEM_VIEW

    if (self.keyShortcut != nil) {
        // 4.1 draw shortcut
        NSRect keyBounds = rectBounds;
        keyBounds.origin.x = keyBounds.size.width - marginRight - shortcutSize.width;
        keyBounds.size.width = shortcutSize.width;
        NSDictionary *keyAttr = [NSDictionary dictionaryWithObjectsAndKeys:
                menuShortcutFont, NSFontAttributeName,
                textColor, NSForegroundColorAttributeName,
                        nil];
        [self.keyShortcut drawInRect:keyBounds withAttributes:keyAttr];

#ifdef VISUAL_DEBUG_CUSTOM_ITEM_VIEW
        [[NSColor magentaColor] set];
        NSFrameRectWithWidth(keyBounds, 1.0f);
#endif // VISUAL_DEBUG_CUSTOM_ITEM_VIEW
    } else {
        if ([owner isKindOfClass:Menu.class]) {
            // 4.2 draw arrow-image of submenu
            NSImage *arrow = [NSImage imageNamed:NSImageNameRightFacingTriangleTemplate]; // TODO: use correct triangle image
            NSRect arrowBounds = rectBounds;
            arrowBounds.origin.x = rectBounds.size.width - marginRight - arrow.size.width;
            arrowBounds.origin.y = rectBounds.origin.y + (rectBounds.size.height - arrow.size.height) / 2;
            arrowBounds.size = arrow.size;
            [arrow drawInRect:arrowBounds];
#ifdef VISUAL_DEBUG_CUSTOM_ITEM_VIEW
            [[NSColor magentaColor] set];
            NSFrameRectWithWidth(arrowBounds, 1.0f);
#endif // VISUAL_DEBUG_CUSTOM_ITEM_VIEW
        }
    }
}

- (void)recalcSizes {
    if (owner == nil || owner->nsMenuItem == nil) return;
    NSString * text = owner->nsMenuItem.title;
    NSImage * image = owner->nsMenuItem.image;

    NSDictionary * attributes = [NSDictionary dictionaryWithObjectsAndKeys:menuFont, NSFontAttributeName, nil];
    textSize = [[[[NSAttributedString alloc] initWithString:text attributes:attributes] autorelease] size];

    NSSize resultSize = NSMakeSize(textSize.width + marginLeft + marginRight, menuItemHeight);

    if (image != nil) {
        NSSize imgSize = image.size;
        resultSize.width += imgSize.width + gapTxtIcon;
    }

    if (self.keyShortcut != nil) {
        NSDictionary * ksa = [NSDictionary dictionaryWithObjectsAndKeys:menuShortcutFont, NSFontAttributeName, nil];
        shortcutSize = [[[[NSAttributedString alloc] initWithString:self.keyShortcut attributes:ksa] autorelease] size];
        resultSize.width += shortcutSize.width + gapTxtShortcut;
    }

    [self.widthAnchor constraintGreaterThanOrEqualToConstant:resultSize.width].active = YES;
}

@end
#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>
#import "JTypes.h"
#import "Utils.h"

@interface NSButtonJAction : NSButton
@property (nonatomic) execute jaction;
- (void)doAction;
@end

@implementation NSButtonJAction
- (void)doAction{
    if (self.jaction) {
        nstrace(@"button [%@]: doAction", self);
        (*self.jaction)();
    } else
        nstrace(@"button [%@]: empty action, nothing to execute", self);
}
@end

static void _setButtonData(NSButtonJAction * button, int buttonWidth, const char * text, const char * raster4ByteRGBA, int w, int h, execute jaction) {
    button.jaction = jaction;
    if (jaction) {
        [button setTarget:button];
        [button setAction:@selector(doAction)];
        [button setEnabled: YES];
    } else {
        [button setTarget:nil];
        [button setAction:NULL];
        [button setEnabled: NO];
    }

    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = getString(text);
    if (nstext == nil)
        [button setImagePosition:NSImageOnly];
    else {
        [button setImagePosition:NSImageLeft];
        [button setTitle:nstext];
    }
    [button setImage:img];
    [button setBezelStyle:NSRoundedBezelStyle];

    if (buttonWidth > 0)
        [button.widthAnchor constraintEqualToConstant:buttonWidth].active = YES;
}

// NOTE: called from AppKit (creation when TB becomes visible)
id createButton(const char * uid, int buttonWidth, const char * text, const char * raster4ByteRGBA, int w, int h, execute jaction) {
    NSString * nsUid = getString(uid);
    nstrace(@"create button [%@]", nsUid);

    NSCustomTouchBarItem * customItemForButton = [[NSCustomTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSButtonJAction * button = [[[NSButtonJAction alloc] init] autorelease];
    _setButtonData(button, buttonWidth, text, raster4ByteRGBA, w, h, jaction);
    customItemForButton.view = button; // NOTE: view is strong
    return customItemForButton;
}

// NOTE: called from EDT (when update UI)
void updateButton(id buttObj, int buttonWidth, const char * text, const char * raster4ByteRGBA, int w, int h, execute jaction) {
    NSCustomTouchBarItem * container = buttObj; // TODO: check types
    nstrace(@"update button [%@]", container.identifier);

    NSButtonJAction * button = (container).view; // TODO: check types
    _setButtonData(button, buttonWidth, text, raster4ByteRGBA, w, h, jaction);
}


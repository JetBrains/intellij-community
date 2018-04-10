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

static void _setButtonData(NSButtonJAction * button, int buttonWidth, NSString * nstext, NSImage * img, execute jaction) {
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
    nstrace(@"create button [%@] (thread: %@)", nsUid, [NSThread currentThread]);

    NSCustomTouchBarItem * customItemForButton = [[NSCustomTouchBarItem alloc] initWithIdentifier:nsUid]; // create non-autorelease object to be owned by java-wrapper
    NSButtonJAction * button = [[[NSButtonJAction alloc] init] autorelease];
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = getString(text);
    _setButtonData(button, buttonWidth, nstext, img, jaction);
    customItemForButton.view = button; // NOTE: view is strong
    return customItemForButton;
}

// NOTE: called from EDT (when update UI)
void updateButton(id buttObj, int buttonWidth, const char * text, const char * raster4ByteRGBA, int w, int h, execute jaction) {
    NSCustomTouchBarItem * container = buttObj; // TODO: check types
    nstrace(@"async update button [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
    NSButtonJAction * button = (container).view; // TODO: check types
    NSAutoreleasePool * edtPool = [[NSAutoreleasePool alloc] init];
    NSImage * img = createImgFrom4ByteRGBA((const unsigned char *)raster4ByteRGBA, w, h);
    NSString * nstext = getString(text);
    dispatch_async(dispatch_get_main_queue(), ^{
        // NOTE: block is copied, img/text objects is automatically retained
//        nstrace(@"\tperform update button [%@] (thread: %@)", container.identifier, [NSThread currentThread]);
        _setButtonData(button, buttonWidth, nstext, img, jaction);
    });
    [edtPool release];
}

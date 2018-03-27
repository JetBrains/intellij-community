#import "ButtonDesc.h"
#import "Utils.h"

@interface ButtonDesc() {
    execute _action;
}
@property (retain, nonatomic) NSImage * img;
@property (retain, nonatomic) NSString * text;
@end

@implementation ButtonDesc
- (id)init:(NSImage *)img text:(NSString*)text act:(execute)act {
    self = [super init];
    if (self) {
        _action = act;
        self.text = text;
        self.img = img;
    }
    return self;
}
- (void)doAction{
    nstrace(@"doAction [%@]", self.uid);
    (*_action)();
}
- (nullable __kindof NSTouchBarItem *)create {
    nstrace(@"create button.imgAndText [%@]", self.uid);

    NSButton *button = [[[NSButton alloc] init] autorelease];
    if (self.text == nil)
        [button setImagePosition:NSImageOnly];
    else {
        [button setImagePosition:NSImageLeft];
        [button setTitle:self.text];
    }
    [button setImage:self.img];
    [button setTarget:self];
    [button setAction:@selector(doAction)];
    [button setBezelStyle:NSRoundedBezelStyle];

    NSCustomTouchBarItem *customItemForButton = [[[NSCustomTouchBarItem alloc] initWithIdentifier:self.uid] autorelease];
    customItemForButton.view = button; // NOTE: view is strong
    return customItemForButton;
}
@synthesize img, text;
@end


#import <Cocoa/Cocoa.h>

typedef  void (*callback)(void);

@interface ItemDesc : NSObject
{
    NSString * uid;
    NSString * type;
    NSString * text;
    callback action;
}
- (id)init:(char*)puid type:(char*)ptype text:(char*)ptext act:(callback)act;

// TODO: make props non-atomic
@property (readonly) NSString * getUid;
@property (readonly) NSString * getType;
@property (readonly) NSString * getText;
@property (readonly) callback getAction;

@end

@implementation ItemDesc
- (id)init:(char*)puid type:(char*)ptype text:(char*)ptext act:(callback)act {
    self = [super init];
    if (self) {
        uid = [NSString stringWithUTF8String:puid];
        type = [NSString stringWithUTF8String:ptype];
        text = [NSString stringWithUTF8String:ptext];
        action = act;
    }
    return self;
}
@synthesize getUid = uid, getType = type, getText = text, getAction = action;
@end

static NSMutableDictionary *g_uid2desc = nil;

void registerItem(char* uid, char* type, char* text, callback action) {
    ItemDesc * idesc = [[ItemDesc alloc] init:uid type:type text:text act:action];
    if (idesc == nil) {
        // TODO: log error
        return;
    }
    if (g_uid2desc == nil)
        g_uid2desc = [[NSMutableDictionary alloc] init];
    g_uid2desc[idesc.getUid] = idesc;
}

@interface NSButtonWithID : NSButton
{
    NSString * uid;
}
@property (retain) NSString * uid;
@end

@implementation NSButtonWithID
@synthesize uid;
@end

@interface NSTDelegate : NSObject<NSTouchBarDelegate>
- (void)buttonExec:(id)sender;
@end


// TODO: implement normal error logging
@implementation NSTDelegate

// This gets called while the NSTouchBar is being constructed, for each NSTouchBarItem to be created.
- (nullable NSTouchBarItem *)touchBar:(NSTouchBar *)touchBar makeItemForIdentifier:(NSTouchBarItemIdentifier)identifier
{
    if (g_uid2desc == nil) {
        NSLog(@"ERROR: try makeTouchBarItem for item '%@', but global items registry is empty", identifier);
        return nil;
    }
    ItemDesc * idesc = g_uid2desc[identifier];
    if (idesc == nil) {
        NSLog(@"ERROR: called makeTouchBarItem for item '%@' that wasn't registered", identifier);
        return nil;
    }

    if ([idesc.getType isEqualToString:@"label"]) {
        NSTextField *theLabel = [NSTextField labelWithString:NSLocalizedString(@"MyLabel", @"")];

        NSCustomTouchBarItem *customItemForLabel =
        [[NSCustomTouchBarItem alloc] initWithIdentifier:idesc.getText];
        customItemForLabel.view = theLabel;

        // We want this label to always be visible no matter how many items are in the NSTouchBar instance.
        customItemForLabel.visibilityPriority = NSTouchBarItemPriorityHigh;

        return customItemForLabel;
    }
    if ([idesc.getType isEqualToString:@"button"]) {
        SEL selAction = @selector(buttonExec:);
        NSButtonWithID *theButton =  [NSButtonWithID buttonWithTitle:idesc.getText target:self action:selAction];
        theButton.uid = idesc.getUid;

        NSCustomTouchBarItem *customItemForButton =
        [[NSCustomTouchBarItem alloc] initWithIdentifier:identifier];
        customItemForButton.view = theButton;

        // We want this label to always be visible no matter how many items are in the NSTouchBar instance.
        customItemForButton.visibilityPriority = NSTouchBarItemPriorityHigh;
        return customItemForButton;
    }

    NSLog(@"ERROR: called makeTouchBarItem for item '%@' that has unsopported type '%@'", identifier, idesc.getType);
    return nil;
}

- (void)buttonExec:(id)sender {
    NSButtonWithID * butSender = (NSButtonWithID *)sender;
    ItemDesc * idesc = g_uid2desc[butSender.uid];
    (*idesc.getAction)();
}

@end

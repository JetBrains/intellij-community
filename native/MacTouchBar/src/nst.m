#import <Cocoa/Cocoa.h>

typedef  void (*callback)(void);

@interface ItemDesc : NSObject
{
    NSString * myItemId;
    NSString * myType;
}
- (id)init:(NSString*)uid type:(NSString*)type;

@property (readonly, nonatomic) NSString * uid;
@property (readonly, nonatomic) NSString * type;
@end

@interface ButtonDesc : ItemDesc
{
    callback myAction;
}
- (id)init:(NSString*)uid type:(NSString*)type act:(callback)act;
@property (readonly, nonatomic) callback action;
@end

@interface ButtonImgDesc : ButtonDesc
{
    NSData * myImg;
}
- (id)init:(NSString*)uid type:(NSString*)type img:(char*)praster bytes:(int)bytesCount act:(callback)act;
@property (readonly, nonatomic) NSData * imgData;
@end

@interface ButtonTextDesc : ButtonDesc
{
    NSString * myText;
}
- (id)init:(NSString*)uid type:(NSString*)type text:(NSString*)text act:(callback)act;
@property (readonly, nonatomic) NSString * text;
@end


@implementation ItemDesc
- (id)init:(NSString*)_uid type:(NSString*)_type {
    self = [super init];
    if (self) {
        myItemId = _uid;
        [myItemId retain];
        myType = _type;
        [myType retain];
    }
    return self;
}
@synthesize uid = myItemId, type = myType;
@end

@implementation ButtonDesc
- (id)init:(NSString*)_uid type:(NSString*)_type act:(callback)_act {
    self = [super init:_uid type:_type];
    if (self)
        myAction = _act;
    return self;
}
@synthesize action = myAction;
@end

@implementation ButtonImgDesc
- (id)init:(NSString*)_uid type:(NSString*)_type img:(char*)_raster bytes:(int)_bytesCount act:(callback)_act {
    self = [super init:_uid type:_type act:_act];
    if (self) {
        myImg = [NSData dataWithBytes:_raster length:_bytesCount];
        [myImg retain];
    }
    return self;
}
@synthesize imgData = myImg;
@end

@implementation ButtonTextDesc
- (id)init:(NSString*)_uid type:(NSString*)_type text:(NSString*)_text act:(callback)_act {
    self = [super init:_uid type:_type act:_act];
    if (self) {
        myText = _text;
        [myText retain];
    }
    return self;
}
@synthesize text = myText;
@end

// NOTE: futher improvements:
// 1. map per TB-wrapper
// 2. request "item's info" via java-callback called from NSTouchBarDelegate:touchBar:makeItemForIdentifier (alloc resources by request)
static NSMutableDictionary *g_uid2desc = nil;

static void _registerItem(ItemDesc * idesc) {
    if (idesc == nil) {
#ifdef LOGGING_ENABLED
        NSLog(@"ERROR: passed nil into _registerItem");
#endif // LOGGING_ENABLED
        return;
    }
    if (g_uid2desc == nil)
        g_uid2desc = [[NSMutableDictionary alloc] init];
    g_uid2desc[idesc.uid] = idesc;
}

void registerButtonText(char* uid, char* text, callback action) {
    ButtonDesc * bdesc = [[ButtonTextDesc alloc] init:[NSString stringWithUTF8String:uid] type:@"button.text" text:[NSString stringWithUTF8String:text] act:action];
    _registerItem(bdesc);
}

void registerButtonImg(char* uid, char* bytes, int bytesCount, callback action) {
    ButtonDesc * bdesc = [[ButtonImgDesc alloc] init:[NSString stringWithUTF8String:uid] type:@"button.img" img:bytes bytes:bytesCount act:action];
    _registerItem(bdesc);
}

@interface NSButtonWithDesc : NSButton
{
    ButtonDesc * desc;
}
@property (retain) ButtonDesc * desc;
@end

@implementation NSButtonWithDesc
@synthesize desc;
@end

@interface NSTDelegate : NSObject<NSTouchBarDelegate>
- (void)performButtonAction:(id)sender;
@end

//#define LOGGING_ENABLED

@implementation NSTDelegate

// This gets called while the NSTouchBar is being constructed, for each NSTouchBarItem to be created.
- (nullable NSTouchBarItem *)touchBar:(NSTouchBar *)touchBar makeItemForIdentifier:(NSTouchBarItemIdentifier)identifier
{
    if (g_uid2desc == nil) {
#ifdef LOGGING_ENABLED
        NSLog(@"ERROR: called makeTouchBarItem for item '%@', but global items registry is empty", identifier);
#endif // LOGGING_ENABLED
        return nil;
    }
    ItemDesc * idesc = g_uid2desc[identifier];
    if (idesc == nil) {
#ifdef LOGGING_ENABLED
        NSLog(@"ERROR: called makeTouchBarItem for uid '%@' that wasn't registered", identifier);
#endif // LOGGING_ENABLED
        return nil;
    }

    if ([idesc.type isEqualToString:@"button.text"]) {
#ifdef LOGGING_ENABLED
        NSLog(@"TRACE: called makeTouchBarItem for button.text '%@'", identifier);
#endif // LOGGING_ENABLED
        SEL selAction = @selector(performButtonAction:);
        ButtonTextDesc * bdesc = (ButtonTextDesc *) idesc;
        NSButtonWithDesc *theButton =  [NSButtonWithDesc buttonWithTitle:bdesc.text target:self action:selAction];
        theButton.desc = bdesc;
        
        NSCustomTouchBarItem *customItemForButton =
        [[NSCustomTouchBarItem alloc] initWithIdentifier:identifier];
        customItemForButton.view = theButton;
        
        // We want this label to always be visible no matter how many items are in the NSTouchBar instance.
        customItemForButton.visibilityPriority = NSTouchBarItemPriorityHigh;
        return customItemForButton;
    }
    
#ifdef LOGGING_ENABLED
    NSLog(@"ERROR: called makeTouchBarItem for uid '%@' that has unsopported type '%@'", identifier, idesc.type);
#endif // LOGGING_ENABLED
    return nil;
}

- (void)performButtonAction:(id)sender {
    NSButtonWithDesc * butSender = (NSButtonWithDesc *)sender;
#ifdef LOGGING_ENABLED
    NSLog(@"TRACE: called performButtonAction for uid '%@'", butSender.desc.uid);
#endif // LOGGING_ENABLED
    (*butSender.desc.action)();
}

@end


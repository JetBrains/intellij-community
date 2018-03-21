#import "TouchBar.h"
#import "ItemDesc.h"

//#define LOGGING_ENABLED

void nslog(NSString* format, ...) {
#ifdef LOGGING_ENABLED
    va_list argList;
    va_start(argList, format);
    NSString* formattedMessage = [[NSString alloc] initWithFormat: format arguments: argList];
    va_end(argList);
    NSLog(@"%@", formattedMessage);
    [formattedMessage release];
#endif // LOGGING_ENABLED
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

@interface TouchBar() {
    int _counter;                       // for uid generation
    NSString * _name;                   // for debugging/logging
    NSMutableArray<ItemDesc *> * _items;
    NSTouchBar * _touchBar;
}

- (id)init;
- (void)dealloc;

- (void)registerItem:(ItemDesc *)idesc;
- (void)selectAllItemsToShow;

- (void)performButtonAction:(id)sender;

@property (retain, nonatomic) NSString * name;
@property (readonly) NSTouchBar * touchBar;
@end

@implementation TouchBar

@synthesize name = _name;
@synthesize touchBar = _touchBar;

-(id)init {
    self = [super init];
    if (self) {
        _counter = 0;
        _items = [[NSMutableArray alloc] init];
        _touchBar = [[NSTouchBar alloc] init];
        [_touchBar setDelegate:self];           // NOTE: delegate-property of NSTouchBar is weak
    }
    return self;
}

- (void)dealloc {
    [_items release];
    [_touchBar release];
    [super dealloc];
}

-(void)registerItem:(ItemDesc *) idesc {
    if (idesc == nil) {
        nslog(@"ERROR: passed nil item-descriptor");
        return;
    }
    if (![idesc isKindOfClass:[SpacingDesc class]])
        idesc.uid = [NSString stringWithFormat:@"%@.%@.%d", _name, [idesc class], _counter++];
    [_items addObject:idesc];
    nslog(@"TRACE: registered item-descriptor '%@' [%@]", [idesc class], idesc.uid);
}

- (void)selectAllItemsToShow {
    const NSUInteger count = [_items count];
    NSMutableArray<NSTouchBarItemIdentifier> * all = [[NSMutableArray alloc] initWithCapacity:count];
    for (int c = 0; c < count; ++c)
        [all addObject:_items[c].uid];

    [_touchBar setDefaultItemIdentifiers:all];
    [all release];
}

- (nullable NSTouchBarItem *)touchBar:(NSTouchBar *)touchBar makeItemForIdentifier:(NSTouchBarItemIdentifier)identifier {
    const NSUInteger count = [_items count];
    ItemDesc * idesc = nil;
    for (int c = 0; c < count; ++c) {
        if ([_items[c].uid isEqualToString:identifier]) {
            idesc = _items[c];
            break;
        }
    }
    if (idesc == nil) {
        nslog(@"ERROR: called makeTouchBarItem for unknown uid [%@]", identifier);
        return nil;
    }

    if ([idesc isKindOfClass:[ButtonDesc class]]) {
        SEL selAction = @selector(performButtonAction:);
        NSButtonWithDesc *theButton = nil;

        if ([idesc isKindOfClass:[ButtonTextDesc class]]) {
            nslog(@"TRACE: create button.text [%@]", identifier);
            ButtonTextDesc * bdesc = (ButtonTextDesc *) idesc;
            theButton =  [NSButtonWithDesc buttonWithTitle:bdesc.text target:self action:selAction];
            theButton.desc = bdesc;
        } else if ([idesc isKindOfClass:[ButtonImgDesc class]]) {
            nslog(@"TRACE: create button.img [%@]", identifier);
            ButtonImgDesc * bdesc = (ButtonImgDesc *) idesc;
            theButton =  [NSButtonWithDesc buttonWithImage:bdesc.img target:self action:selAction];
            theButton.desc = bdesc;
        } else {
            nslog(@"ERROR: can't create unknown button subtype '%@' [%@]", idesc, identifier);
            return nil;
        }

        NSCustomTouchBarItem *customItemForButton = [[[NSCustomTouchBarItem alloc] initWithIdentifier:identifier] autorelease];
        customItemForButton.view = theButton; // NOTE: view is strong
        return customItemForButton;
    }

    if ([idesc isKindOfClass:[PopoverDesc class]]) {
        nslog(@"TRACE: create popover [%@]", identifier);
        PopoverDesc * pdesc = (PopoverDesc *) idesc;

        NSPopoverTouchBarItem * popoverTouchBarItem = [[[NSPopoverTouchBarItem alloc] initWithIdentifier:pdesc.uid] autorelease];
        popoverTouchBarItem.showsCloseButton = YES;

        if (pdesc.width <= 0) {
            // create 'flexible' view
            if (pdesc.img != nil)
                popoverTouchBarItem.collapsedRepresentationImage = pdesc.img;
            if (pdesc.text != nil)
                popoverTouchBarItem.collapsedRepresentationLabel = pdesc.text;
        } else {
            // create fixed width view
            NSButton *button = [[[NSButton alloc] init] autorelease];
            [button setImagePosition:NSImageLeft];
            [button setImage:pdesc.img];
            [button setTitle:pdesc.text];
            [button setTarget:popoverTouchBarItem];
            [button setAction:@selector(showPopover:)];

            [button setBezelStyle:NSRoundedBezelStyle];
            [button.widthAnchor constraintEqualToConstant:pdesc.width].active = YES;

            popoverTouchBarItem.collapsedRepresentation = button;
        }

        popoverTouchBarItem.popoverTouchBar = pdesc.expandBar.touchBar;
        popoverTouchBarItem.pressAndHoldTouchBar = pdesc.tapHoldBar.touchBar;
        return popoverTouchBarItem;
    }

    nslog(@"ERROR: called makeTouchBarItem for uid '%@' that has unsopported type '%@'", identifier, [idesc class]);
    return nil;
}

- (void)performButtonAction:(id)sender {
    NSButtonWithDesc * butSender = (NSButtonWithDesc *)sender;
    nslog(@"TRACE: called performButtonAction for uid '%@'", butSender.desc.uid);
    (*butSender.desc.action)();
}

@end

id createTouchBar(char * name) {
    TouchBar * res = [[TouchBar alloc] init];
    res.name = [NSString stringWithUTF8String:name];
    return res;
}

void releaseTouchBar(id tbobj) {
    [tbobj release];
}

// NOTE:
// We can obtain "item's descriptor" via java-callback (called from NSTouchBarDelegate:touchBar:makeItemForIdentifier).
// This allows to alloc resources by request but it can produce latency in AppKit-thread theoretically (read icon from file, for example)

id registerButtonText(id tbobj, char* text, callback action) {
    ButtonDesc * bdesc = [[[ButtonTextDesc alloc] init:[NSString stringWithUTF8String:text] act:action] autorelease];
    [(TouchBar *)tbobj registerItem:bdesc];
    return bdesc;
}

id registerButtonImg(id tbobj, char* raster4ByteRGBA, int w, int h, callback action) {
    if (w <= 0 || h <= 0) {
        nslog(@"ERROR: incorrect image sizes");
        return nil;
    }
    ButtonDesc * bdesc = [[[ButtonImgDesc alloc] init:raster4ByteRGBA width:w height:h act:action] autorelease];
    [(TouchBar *)tbobj registerItem:bdesc];
    return bdesc;
}

id registerSpacing(id tbobj, char* type) {
    SpacingDesc * spdesc = [[[SpacingDesc alloc] init:[NSString stringWithUTF8String:type]] autorelease];
    [(TouchBar *)tbobj registerItem:spdesc];
    return spdesc;
}

id registerPopover(id tbobj, char* text, char* raster4ByteRGBA, int ingW, int imgH, int popW) {
    PopoverDesc * pdesc = [[[PopoverDesc alloc] init:[NSString stringWithUTF8String:text] img:raster4ByteRGBA imgW:ingW imgH:imgH popoverWidth:popW] autorelease];
    [(TouchBar *)tbobj registerItem:pdesc];
    return pdesc;
}

void setPopoverExpandTouchBar(id popoverobj, id expandTB) {
    PopoverDesc * pdesc = (PopoverDesc *) popoverobj;
    pdesc.expandBar = expandTB;
    nslog(@"TRACE: set expandTB to popover [%@], text='%@', tb='%@'", pdesc.uid, pdesc.text, pdesc.expandBar);
}

void setPopoverTapAndHoldTouchBar(id popoverobj, id tapHoldTB) {
    PopoverDesc * pdesc = (PopoverDesc *) popoverobj;
    pdesc.tapHoldBar = tapHoldTB;
    nslog(@"TRACE: set tapHoldTB to popover [%@], text='%@', tb='%@'", pdesc.uid, pdesc.text, pdesc.tapHoldBar);
}

void selectAllItemsToShow(id tb) {
    [(TouchBar *)tb selectAllItemsToShow];
}

void setTouchBar(id tb) {
    [[NSApplication sharedApplication] setTouchBar:((TouchBar *)tb).touchBar];
}
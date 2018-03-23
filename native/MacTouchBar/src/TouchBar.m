#import "TouchBar.h"
#import "SpacingDesc.h"
#import "ButtonDesc.h"
#import "PopoverDesc.h"
#import "ScrubberDesc.h"
#import "Utils.h"

@interface TouchBar() {
    int _counter;                       // for uid generation
    NSMutableArray<ItemDesc *> * _items;
    NSTouchBar * _touchBar;
}
- (void)registerItem:(ItemDesc *)idesc;
- (void)selectAllItemsToShow;

@property (retain, nonatomic) NSString * name; // for debugging/logging
@end

@implementation TouchBar

@synthesize name;

-(id)init {
    self = [super init];
    if (self) {
        _counter = 0;
        _items = [[NSMutableArray alloc] init];
        _touchBar = [[NSTouchBar alloc] init];
        [_touchBar setDelegate:self];          // NOTE: delegate-property of NSTouchBar is weak
    }
    return self;
}

- (void)dealloc {
    [_touchBar release];
    [_items release];
    [super dealloc];
}

-(void)registerItem:(ItemDesc *) idesc {
    if (idesc == nil) {
        nserror(@"passed nil item-descriptor");
        return;
    }
    if (![idesc isKindOfClass:[SpacingDesc class]])
        idesc.uid = [NSString stringWithFormat:@"%@.%@.%d", name, [idesc class], _counter++];
    [_items addObject:idesc];
    nstrace(@"registered item-descriptor '%@' [%@]", [idesc class], idesc.uid);
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
        nserror(@"called makeTouchBarItem for unknown uid [%@]", identifier);
        return nil;
    }

    NSTouchBarItem * result = [idesc create];
    if (result == nil)
        nserror(@"can't make TouchBarItem for uid '%@' of type '%@'", identifier, [idesc class]);
    return result;
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

id registerButtonText(id tbobj, char* text, execute action) {
    ButtonDesc * bdesc = [[[ButtonTextDesc alloc] init:[NSString stringWithUTF8String:text] act:action] autorelease];
    [(TouchBar *)tbobj registerItem:bdesc];
    return bdesc;
}

id registerButtonImg(id tbobj, char* raster4ByteRGBA, int w, int h, execute action) {
    if (w <= 0 || h <= 0) {
        nserror(@"incorrect image sizes");
        return nil;
    }
    ButtonDesc * bdesc = [[[ButtonImgDesc alloc] init:createImgFrom4ByteRGBA((unsigned char *)raster4ByteRGBA, w, h) act:action] autorelease];
    [(TouchBar *)tbobj registerItem:bdesc];
    return bdesc;
}

id registerSpacing(id tbobj, char* type) {
    SpacingDesc * spdesc = [[[SpacingDesc alloc] init:[NSString stringWithUTF8String:type]] autorelease];
    [(TouchBar *)tbobj registerItem:spdesc];
    return spdesc;
}

id registerPopover(id tbobj, char* text, char* raster4ByteRGBA, int w, int h, int popW) {
    PopoverDesc * pdesc = [[[PopoverDesc alloc] init:[NSString stringWithUTF8String:text] img:createImgFrom4ByteRGBA((unsigned char *)raster4ByteRGBA, w, h) popoverWidth:popW] autorelease];
    [(TouchBar *)tbobj registerItem:pdesc];
    return pdesc;
}

id registerScrubber(id tbobj, int scrubW) {
    ScrubberDesc * sdesc = [[[ScrubberDesc alloc] init:scrubW] autorelease];
    [(TouchBar *)tbobj registerItem:sdesc];
    return sdesc;
}

void selectAllItemsToShow(id tb) {
    [(TouchBar *)tb selectAllItemsToShow];
}

void setTouchBar(id tb) {
    [[NSApplication sharedApplication] setTouchBar:((TouchBar *)tb).touchBar];
}
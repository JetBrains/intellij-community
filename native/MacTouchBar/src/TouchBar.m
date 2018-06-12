#import "TouchBar.h"
#import "Utils.h"

@interface TouchBar() {
    createItem _jcreator;
}
@end

@implementation TouchBar

-(id)init:(NSString *)name jcreator:(createItem)jcreator customEscId:(NSString *)escId {
    self = [super init];
    if (self) {
        _jcreator = jcreator;
        self.name = name;
        self.touchBar = [[[NSTouchBar alloc] init] autorelease];
        self.touchBar.delegate = self;          // NOTE: delegate-property of NSTouchBar is weak
        if (escId != nil && [escId length] > 0)
            self.touchBar.escapeKeyReplacementItemIdentifier = escId;
    }
    return self;
}

- (nullable NSTouchBarItem *)touchBar:(NSTouchBar *)touchBar makeItemForIdentifier:(NSTouchBarItemIdentifier)identifier {
    // NOTE: called from AppKit-thread
    if (_jcreator == nil) {
        nserror(@"tb [%@]: called makeTouchBarItem for '%@' but creator is null", self.name, identifier);
        return nil;
    }

    NSTouchBarItem * result = (*_jcreator)([identifier UTF8String]);
    if (result == nil)
        nserror(@"tb [%@]: can't make item for uid '%@'", identifier);
    return result;
}

@end

//
// NOTE: next functions are called only from EDT
//

void selectItemsToShow(id touchBar, const char** ppIds, int count) {
    NSAutoreleasePool * edtPool = [[NSAutoreleasePool alloc] init];
    NSMutableArray<NSTouchBarItemIdentifier> * all = [[[NSMutableArray alloc] initWithCapacity:count] autorelease];
    for (int c = 0; c < count; ++c) {
        const char * pId = ppIds[c];
        if (!pId)
            continue;

        NSString * nsId = [NSString stringWithUTF8String:pId];
        if ([nsId isEqualToString:@"static_touchbar_item_small_space"]) {
            [all addObject:NSTouchBarItemIdentifierFixedSpaceSmall];
        } else if ([nsId isEqualToString:@"static_touchbar_item_large_space"]) {
            [all addObject:NSTouchBarItemIdentifierFixedSpaceLarge];
        } else if ([nsId isEqualToString:@"static_touchbar_item_flexible_space"]) {
            [all addObject:NSTouchBarItemIdentifierFlexibleSpace];
        } else
            [all addObject:nsId];
    }

    TouchBar * tb = (TouchBar *)touchBar; // TODO: check types
    dispatch_async(dispatch_get_main_queue(), ^{
        [tb.touchBar setDefaultItemIdentifiers:all];
    });
    [edtPool release];
}

id createTouchBar(const char * name, createItem jcreator, const char * escId) {
    NSAutoreleasePool * edtPool = [[NSAutoreleasePool alloc] init];
    TouchBar * result = [[TouchBar alloc] init:getString(name) jcreator:jcreator customEscId:getString(escId)]; // creates non-autorelease obj to be owned by java-wrapper
    [edtPool release];
    return result;
}

void setPrincipal(id tbobj, const char * uid) {
    TouchBar * tb = (TouchBar *)tbobj; // TODO: check types
    [tb.touchBar setPrincipalItemIdentifier:getString(uid)];
}

void releaseTouchBar(id tbobj) {
    [tbobj release];
}

id createGroupItem(const char * uid, id * items, int count) {
    NSMutableArray *allItems = [NSMutableArray arrayWithCapacity:count];
    for (int c = 0; c < count; ++c)
        [allItems addObject:items[c]];

    NSGroupTouchBarItem * result = [NSGroupTouchBarItem groupItemWithIdentifier:getString(uid) items:allItems];
    // NOTE: should create non-autorelease object to be owned by java-wrapper
    // the simplest way to create working NSGroupTouchBarItem with fixed item set is to call groupItemWithIdentifier, which creates autorelease object, so do retain..
    [result retain];
    return result;
}

void setTouchBar(id tb) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [[NSApplication sharedApplication] setTouchBar:((TouchBar *) tb).touchBar];
    });
}

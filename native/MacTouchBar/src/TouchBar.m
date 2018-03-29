#import "TouchBar.h"
#import "Utils.h"

@interface TouchBar() {
    createItem _jcreator;
}
@end

@implementation TouchBar

-(id)init:(NSString *)name jcreator:(createItem)jcreator {
    self = [super init];
    if (self) {
        _jcreator = jcreator;
        self.name = name;
        self.touchBar = [[[NSTouchBar alloc] init] autorelease];
        self.touchBar.delegate = self;          // NOTE: delegate-property of NSTouchBar is weak
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
    NSMutableArray<NSTouchBarItemIdentifier> * all = [[NSMutableArray alloc] initWithCapacity:count];
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
    [tb.touchBar setDefaultItemIdentifiers:all];
    [all release];
}

id createTouchBar(const char * name, createItem jcreator) {
    return [[TouchBar alloc] init:getString(name) jcreator:jcreator]; // creates non-autorelease obj to be owned by java-wrapper
}

void releaseTouchBar(id tbobj) {
    [tbobj release];
}

void setTouchBar(id tb) {
    [[NSApplication sharedApplication] setTouchBar:((TouchBar *)tb).touchBar];
}

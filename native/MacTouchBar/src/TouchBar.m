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
    // NOTE: called from AppKit-thread, uses default autorelease-pool (create before event processing)
    if (_jcreator == nil) {
        nserror(@"tb [%@]: called makeTouchBarItem for '%@' but creator is null", self.name, identifier);
        return nil;
    }

    NSTouchBarItem * result = (*_jcreator)([identifier UTF8String]);
    if (result == nil)
        nserror(@"tb [%@]: can't make item for uid '%@'", self.name, identifier);
    return result;
}

@end

//
// NOTE: next functions are called only from EDT
//
__used
void selectItemsToShow(id touchBar, const char** ppIds, int count) {
    @autoreleasepool {
        NSMutableArray<NSTouchBarItemIdentifier> *all = [[[NSMutableArray alloc] initWithCapacity:count] autorelease];
        for (int c = 0; c < count; ++c) {
            const char *pId = ppIds[c];
            if (!pId)
                continue;

            NSString *nsId = [NSString stringWithUTF8String:pId];
            if ([nsId isEqualToString:@"static_touchbar_item_small_space"]) {
                [all addObject:NSTouchBarItemIdentifierFixedSpaceSmall];
            } else if ([nsId isEqualToString:@"static_touchbar_item_large_space"]) {
                [all addObject:NSTouchBarItemIdentifierFixedSpaceLarge];
            } else if ([nsId isEqualToString:@"static_touchbar_item_flexible_space"]) {
                [all addObject:NSTouchBarItemIdentifierFlexibleSpace];
            } else
                [all addObject:nsId];
        }

        TouchBar *tb = (TouchBar *) touchBar;
        dispatch_async(dispatch_get_main_queue(), ^{
            [tb.touchBar setDefaultItemIdentifiers:all];
        });
    }
}

// NOTE: called from EDT (when java-wrapper of touchbar created)
__used
NS_RETURNS_RETAINED
id createTouchBar(const char * name, createItem jcreator, const char * escId) {
    @autoreleasepool {
        return [[TouchBar alloc] init:createStringFromUTF8(name) jcreator:jcreator customEscId:createStringFromUTF8(escId)]; // creates non-autorelease obj to be owned by java-wrapper
    }
}

__used
void setPrincipal(id tbobj, const char * uid) {
    @autoreleasepool {
        TouchBar *tb = (TouchBar *) tbobj;
        [tb.touchBar setPrincipalItemIdentifier:createStringFromUTF8(uid)];
    }
}

__used
void releaseNativePeer(id tbobj) {
    void (^doRelease)() = ^{
        if ([tbobj isKindOfClass:[NSCustomTouchBarItem class]])
            ((NSCustomTouchBarItem *)tbobj).view = nil;
        [tbobj release];
    };
      if ([NSThread isMainThread]) {
          doRelease();
      } else {
          dispatch_async(dispatch_get_main_queue(), ^{
              doRelease();
          });
      }
}

// NOTE: called from AppKit-thread (creation when TB becomes visible), uses default autorelease-pool (create before event processing)
__used
NS_RETURNS_RETAINED
id createGroupItem(const char * uid, id * items, int count) {
    NSMutableArray *allItems = [NSMutableArray arrayWithCapacity:count];
    for (int c = 0; c < count; ++c)
        [allItems addObject:items[c]];

    NSGroupTouchBarItem * result = [NSGroupTouchBarItem groupItemWithIdentifier:createStringFromUTF8(uid) items:allItems];
    // NOTE: should create non-autorelease object to be owned by java-wrapper
    // the simplest way to create working NSGroupTouchBarItem with fixed item set is to call groupItemWithIdentifier, which creates autorelease object, so do retain..
    [result retain];
    return result;
}

__used
void setTouchBar(id nsview, id tb) {
    NSObject * responder = nsview == 0 ? [NSApplication sharedApplication] : nsview;
    if ([responder isKindOfClass:[NSResponder class]]) {
      dispatch_async(dispatch_get_main_queue(), ^{
          [(NSResponder *)responder setTouchBar:((TouchBar *) tb).touchBar];
      });
    } else {
      nserror(@"not a responder %d", nsview);
    }
}

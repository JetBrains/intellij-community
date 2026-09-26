#import "Menu.h"
#import "CustomMenuItemView.h"

static jclass sjc_Menu = NULL;
#define GET_MENU_CLASS() GET_CLASS(sjc_Menu, "com/intellij/ui/mac/screenmenu/Menu");

//
// Menu (NSMenu wrapper)
//

static NSTimeInterval sLastUpdate = -1; // Used only in AppKit.
static bool sUpdateInProgress = false; // Used only in AppKit.
static const int RELAX_AFTER_UPDATE_MS = 500;

@implementation Menu

// returns retained object (java peer must release it)
- (id)initWithPeer:(jobject)peer {
    self = [super initWithPeer:peer asSeparator:NO];
    if (self) {
        nsMenu = [[NSMenu alloc] init];
        [nsMenu setAutoenablesItems:NO];
        nsMenu.delegate = self;
    }
    return self;
}

- (id)initWithNSObject:(NSMenu *)nsMenuPtr javaPeer:(jobject)peer {
    // NOTE: should we use here something like NSMenu.getItem ?
    self = [super initWithPeer:peer asSeparator:NO];
    if (self) {
        nsMenu = nsMenuPtr; // must be already retained
    }
    return self;
}

- (void)dealloc {
    [nsMenu release];
    nsMenu = nil;
    [super dealloc];
}

//
// NSMenuDelegate methods
// All of them are called from AppKit
//

- (void)menuNeedsUpdate:(NSMenu *)menu {
    if (javaPeer == nil)
        return;

    if (sUpdateInProgress) {
        // Nested updates can lead to crash, see IDEA-315910
        //fprintf(stderr, "skip nested update for menu '%s'\n", [sCurrentMenu.title cString]);
        return;
    }

    JNIEnv *env = getAppKitEnv();
    JNI_COCOA_ENTER();
    GET_MENU_CLASS();
    DECLARE_METHOD(jm_Menu_menuNeedsUpdate, sjc_Menu, "menuNeedsUpdate", "()V");

    @try {
        sLastUpdate = [[NSDate date] timeIntervalSinceReferenceDate];
        sUpdateInProgress = true;
        (*env)->CallVoidMethod(env, javaPeer, jm_Menu_menuNeedsUpdate);
    }
    @finally {
        sUpdateInProgress = false;
    }
    CHECK_EXCEPTION(env);
    JNI_COCOA_EXIT();
}

- (void)menuWillOpen:(NSMenu *)menu {
    if (javaPeer == nil)
        return;

    JNIEnv *env = getAppKitEnv();
    JNI_COCOA_ENTER();
    GET_MENU_CLASS();
    DECLARE_METHOD(jm_Menu_menuWillOpen, sjc_Menu, "menuWillOpen", "()V");
    (*env)->CallVoidMethod(env, javaPeer, jm_Menu_menuWillOpen);
    CHECK_EXCEPTION(env);
    JNI_COCOA_EXIT();
}

- (void)menuDidClose:(NSMenu *)menu {
    if (javaPeer == nil)
        return;

    JNIEnv *env = getAppKitEnv();
    JNI_COCOA_ENTER();
    GET_MENU_CLASS();
    DECLARE_METHOD(jm_Menu_invokeMenuClosing, sjc_Menu, "invokeMenuClosing", "()V");
    (*env)->CallVoidMethod(env, javaPeer, jm_Menu_invokeMenuClosing);
    CHECK_EXCEPTION(env);
    JNI_COCOA_EXIT();
}

- (void)menu:(NSMenu *)menu willHighlightItem:(NSMenuItem *)item {
    for (NSMenuItem * child in menu.itemArray) {
        if (child != nil && [child.view isKindOfClass:CustomMenuItemView.class]) {
            [(CustomMenuItemView *)child.view setSelected:(child == item)];
        }
    }
}

- (BOOL)menuHasKeyEquivalent:(NSMenu *)menu
                    forEvent:(NSEvent *)event
                      target:(id  _Nullable *)target
                      action:(SEL  _Nullable *)action {
    return NO;
}

//
// Other methods
//

- (void)setTitle:(NSString *)title {
    if (title == nil)
        return;

    [nsMenu setTitle:title];
    [nsMenuItem setTitle:title];
}

- (void)addItem:(MenuItem *)itemModified {
    if ([itemModified isKindOfClass:[Menu class]]) {
        // "validate" itemModified (i.e. connect corresponding fMenuItem and fMenu)
        Menu * menuModified = (Menu *)itemModified;
        [menuModified->nsMenuItem setSubmenu:menuModified->nsMenu];
    }

    [nsMenu addItem:itemModified->nsMenuItem];
}

- (NSString *)description {
    return [NSString stringWithFormat:@"Menu[ %@ ]", nsMenu];
}

@end

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeCreateMenu
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeCreateMenu
(JNIEnv *env, jobject peer)
{
    initGlobalVMPtr(env);

    JNI_COCOA_ENTER();
    jobject cPeerObjGlobal = (*env)->NewGlobalRef(env, peer);
    // NOTE: returns retained Menu
    // Java owner must release it manually via nativeDisposeMenu (see Menu.dispose())
    Menu * menu = [[Menu alloc] initWithPeer:cPeerObjGlobal];
    // Set non empty title, just for insurance (that item won't be considered as empty and will be displayed)
    [menu->nsMenu setTitle:@" "];
    [menu->nsMenuItem setTitle:@" "];
    return (jlong)menu;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeAttachMenu
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeAttachMenu
(JNIEnv *env, jobject peer, jlong nsMenu)
{
    initGlobalVMPtr(env);

    JNI_COCOA_ENTER();
    jobject javaPeer = (*env)->NewGlobalRef(env, peer);
    // NOTE: returns retained Menu
    // Java owner must release it manually via nativeDispose (see Menu.dispose())
    Menu * menu = [[Menu alloc] initWithNSObject:(NSMenu*)nsMenu javaPeer:javaPeer];
    return (jlong)menu;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeSetTitle
 * Signature: (JLjava/lang/String;Z)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeSetTitle
(JNIEnv *env, jobject peer, jlong menuObj, jstring label, jboolean onAppKit)
{
    JNI_COCOA_ENTER();
    __strong NSString *theText = JavaStringToNSString(env, label);
    __strong Menu * menu = (Menu *)menuObj;
    dispatch_block_t block = ^{
        [menu setTitle:theText];
    };
    if (!onAppKit || [NSThread isMainThread]) {
        block();
    } else {
        dispatch_async(dispatch_get_main_queue(), block);
    }
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeAddItem
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeAddItem
(JNIEnv *env, jobject peer, jlong menuPtr, jlong itemToAdd, jboolean onAppKit)
{
    JNI_COCOA_ENTER();
    Menu * __strong menu = (Menu *)menuPtr;
    MenuItem * __strong child = (MenuItem *)itemToAdd;
    if (!onAppKit || [NSThread isMainThread])
        [menu addItem:child];
    else
        dispatch_async(dispatch_get_main_queue(), ^{
            [menu addItem:child];
        });
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeInsertItem
 * Signature: (JJIZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeInsertItem
(JNIEnv *env, jobject peer, jlong menuPtr, jlong itemToAdd, jint position, jboolean onAppKit)
{
    if (position < 0) return;

    JNI_COCOA_ENTER();
    Menu * __strong menu = (Menu *)menuPtr;
    MenuItem * __strong child = (MenuItem *)itemToAdd;
    dispatch_block_t block = ^{
        if (position < menu->nsMenu.numberOfItems) {
            [menu->nsMenu insertItem:child->nsMenuItem atIndex:position];
        } else {
            NSLog(@"ERROR: incorrect position %d (numberOfItems=%d, menu: %@)", position, (int)(menu->nsMenu.numberOfItems), menu->nsMenu);
        }
    };
    if (!onAppKit || [NSThread isMainThread]) {
        block();
    } else {
        dispatch_async(dispatch_get_main_queue(), block);
    }
    JNI_COCOA_EXIT();
}


@interface MenuRefiller : NSObject {
@public
    Menu * menu;
    jsize length;
    jlong * newItemsPtrs;
}

- (id)initWithNewItems:(jlong)menuObj items:(jlongArray)newItems jniEnv:(JNIEnv *)env;
- (void)dealloc;

- (void)refill;
@end

@implementation MenuRefiller

- (id)initWithNewItems:(jlong)menuObj items:(jlongArray)newItems jniEnv:(JNIEnv *)env {
    self = [super init];
    if (self) {
        // create copy of array
        menu = (Menu *)menuObj;
        [menu retain];

        length = 0;
        newItemsPtrs = NULL;
        if (newItems != NULL) {
            length = (*env)->GetArrayLength(env, newItems);
            if (length > 0) {
                size_t lengthInBytes = length * sizeof(long);
                newItemsPtrs = (jlong *) malloc(lengthInBytes);
                jlong *ptrs = (*env)->GetLongArrayElements(env, newItems, NULL);
                memcpy(newItemsPtrs, ptrs, lengthInBytes);
                (*env)->ReleaseLongArrayElements(env, newItems, ptrs, 0);

                // retain new items
                for (int i = 0; i < length; i++) {
                    id newItem = (id) (newItemsPtrs[i]);
                    if (newItem != NULL) {
                        [newItem retain];
                    }
                }
            }
        }
    }
    return self;
}

- (void)dealloc {
    [menu release];

    if (newItemsPtrs != NULL)
        free(newItemsPtrs);

    [super dealloc];
}

- (void)refill {
    // Called from AppKit only
    if (menu == nil) {
        // Going to update main menu. Will defer update in some cases (see IDEA-315910)
        bool defer = false;
        if (sUpdateInProgress) {
            // Don't change hierachy when update in progress
            defer = true;
        } else if (sLastUpdate > 0) {
            NSTimeInterval delta = [[NSDate date] timeIntervalSinceReferenceDate] - sLastUpdate;
            long deltaMs = delta*1000;
            if (deltaMs < RELAX_AFTER_UPDATE_MS) {
                // Don't change hierachy immediately after last update
                defer = true;
            }
        }
        if (defer) {
            //fprintf(stderr, "defer update of main menu\n");
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 500 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{[self refill];});
            return;
        }
    }

    NSMenu * mainMenu = menu == nil ? [NSApplication sharedApplication].mainMenu : nil;

    //
    // clear old items
    //
    NSInteger countBefore = 0;
    if (menu != nil) {
        // NOTE: when use [menu->nsMenu removeAllItems] => selection is dropped
        // so don't allow menu to be empty: remove all except first
        countBefore = [menu->nsMenu numberOfItems];
        for (int i = [menu->nsMenu numberOfItems]; i - 1 > 0; i--) {
            [menu->nsMenu removeItemAtIndex:i - 1];
        }
    } else {
        // clear Main Menu: remove all except first (AppMenu)
        if ([mainMenu numberOfItems] > 0) {
            for (int i = [mainMenu numberOfItems]; i - 1 > 0; i--) {
                [mainMenu removeItemAtIndex:i - 1];
            }
        }
    }

    //
    // add new items
    //
    if (newItemsPtrs != NULL) {
        for (int i = 0; i < length; i++) {
            MenuItem *child = (MenuItem *) newItemsPtrs[i];
            if (child == NULL) {
                // add separator
                if (menu != nil)
                    [menu->nsMenu addItem:[NSMenuItem separatorItem]];
                else
                    [mainMenu addItem:[NSMenuItem separatorItem]];
            } else {
                // add menu item
                if (menu != nil)
                    [menu addItem:child];
                else {
                    if ([child isKindOfClass:[Menu class]]) {
                        // "validate", just for insurance
                        Menu *menuModified = (Menu *) child;
                        [menuModified->nsMenuItem setSubmenu:menuModified->nsMenu];
                    }
                    [mainMenu addItem:child->nsMenuItem];
                }
                [child release];
            }
        }
    }

    // remove first item (that wasn't removed before adding)
    if (countBefore > 0) {
        [menu->nsMenu removeItemAtIndex:0];
    }

    [self release];
}

@end

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeRefill
 * Signature: (J[JZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeRefill
(JNIEnv *env, jobject peer, jlong menuObj, jlongArray newItems, jboolean onAppKit)
{
    JNI_COCOA_ENTER();

    MenuRefiller * refiller = [[MenuRefiller alloc] initWithNewItems:menuObj items:newItems jniEnv:env];

    dispatch_block_t block = ^{
        [refiller refill];
    };

    if (onAppKit && ![NSThread isMainThread])
        dispatch_async(dispatch_get_main_queue(), block);
    else
        block();

    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeInitClass
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeInitClass(JNIEnv *env, jclass peerClass) {
    if (sjc_Menu == NULL) {
        // Cache Menu jclass, because JNI can't find it when:
        // 1. class in signed JAR
        // 2. class requested in AppKit-thread
        sjc_Menu = peerClass;
        if (sjc_Menu != NULL) {
            sjc_Menu = (*env)->NewGlobalRef(env, sjc_Menu);
        }
    }
}

static NSMenuItem* findItemByTitle(NSMenu* menu, NSString* re) {
    NSError *error = NULL;
    NSRegularExpression *regex = [NSRegularExpression
            regularExpressionWithPattern:re
                                 options:NSRegularExpressionCaseInsensitive
                                   error:&error];

    for (id object in menu.itemArray) {
        NSMenuItem* item = (NSMenuItem *)object;
        if (item.title != nil) {
            NSTextCheckingResult *match = [regex firstMatchInString:item.title options:0 range:NSMakeRange(0, [item.title length])];
            if (match != nil && match.numberOfRanges > 0) {
                return item;
            }
        }
    }
    return nil;
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeFindIndexByTitle
 * Signature: (JLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeFindIndexByTitle(JNIEnv *env, jobject peer, jlong menuObj, jstring re) {
    JNI_COCOA_ENTER();
    __strong Menu * menu = (Menu *)menuObj;
    __strong NSString* regexp = JavaStringToNSString(env, re);
    __block int index = -1;
    dispatch_block_t block = ^{
        NSMenuItem* result = findItemByTitle(menu->nsMenu, regexp);
        if (result != nil) index = [menu->nsMenu indexOfItem:result];
    };
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_async_and_wait(dispatch_get_main_queue(), block);
    }
    return index;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeFindItemByTitle
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeFindItemByTitle(JNIEnv *env, jobject peer, jlong menuObj, jstring re) {
    JNI_COCOA_ENTER();
    __strong Menu * menu = (Menu *)menuObj;
    __strong NSString* regexp = JavaStringToNSString(env, re);
    __block long itemPtr = 0;
    dispatch_block_t block = ^{
        NSMenuItem* result = findItemByTitle(menu->nsMenu, regexp);
        if (result != nil) itemPtr = (long)[result retain];
    };
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_async_and_wait(dispatch_get_main_queue(), block);
    }
    return itemPtr;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeGetAppMenu
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeGetAppMenu(JNIEnv *env, jclass peerClass) {
    JNI_COCOA_ENTER();
    __block id appMenu = nil;
    dispatch_block_t block = ^{
        NSMenu * mainMenu = [NSApplication sharedApplication].mainMenu;
        appMenu = [mainMenu numberOfItems] > 0 ? [mainMenu itemAtIndex:0] : nil;
        if (appMenu != nil) {
            appMenu = [appMenu submenu];
            [appMenu retain];
        }
    };
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_async_and_wait(dispatch_get_main_queue(), block);
    }
    return (jlong)appMenu;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_Menu
 * Method:    nativeRenameAppMenuItems
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeRenameAppMenuItems(JNIEnv *env, jclass peerClass, jobjectArray jStringArray) {
    JNI_COCOA_ENTER();
    const int stringCount = (*env)->GetArrayLength(env, jStringArray);
    if (stringCount < 2) return; // simple protection (just for insurance)
    NSMutableArray * __strong stringArray = [NSMutableArray arrayWithCapacity:stringCount];
    for (int i = 0; i < stringCount; i += 2) {
        jstring jTitle = (jstring) ((*env)->GetObjectArrayElement(env, jStringArray, i));
        [stringArray addObject:JavaStringToNSString(env, jTitle)];
        if (i + 1 >= stringCount) return; // simple protection (just for insurance)
        jstring jLocalizedTitle = (jstring) ((*env)->GetObjectArrayElement(env, jStringArray, i + 1));
        [stringArray addObject:JavaStringToNSString(env, jLocalizedTitle)];
    }

    dispatch_block_t block = ^{
        NSMenu * mainMenu = [NSApplication sharedApplication].mainMenu;
        id appMenu = [mainMenu numberOfItems] > 0 ? [mainMenu itemAtIndex:0] : nil;
        if (appMenu != nil) {
            appMenu = [appMenu submenu];
        }
        for (int i = 0; i < stringCount; i += 2) {
            NSString * title = [stringArray objectAtIndex:i];
            NSString * localizedTitle = [stringArray objectAtIndex:i + 1];
            NSMenuItem * item = findItemByTitle(appMenu, title);
            if (item != nil) {
                 [item setTitle:localizedTitle];
                 if ([item.view isKindOfClass:CustomMenuItemView.class]) {
                     [(CustomMenuItemView *)(item.view) recalcSizes];
                 }
            }
        }
    };
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_async(dispatch_get_main_queue(), block);
    }
    JNI_COCOA_EXIT();
}

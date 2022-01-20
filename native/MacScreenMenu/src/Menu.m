#import "Menu.h"
#import "CustomMenuItemView.h"

static jclass sjc_Menu = NULL;
#define GET_MENU_CLASS() GET_CLASS(sjc_Menu, "com/intellij/ui/mac/screenmenu/Menu");

//
// Menu (NSMenu wrapper)
//

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

- (void)dealloc {
    [nsMenu release];
    nsMenu = nil;
    [super dealloc];
}

// NSMenuDelegate

- (void)menuWillOpen:(NSMenu *)menu {
    if (javaPeer == nil)
        return;

    JNIEnv *env = getAppKitEnv();
    JNI_COCOA_ENTER();
    GET_MENU_CLASS();
    DECLARE_METHOD(jm_Menu_invokeOpenLater, sjc_Menu, "invokeOpenLater", "()V");
    (*env)->CallVoidMethod(env, javaPeer, jm_Menu_invokeOpenLater);
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
 * Method:    nativeRefill
 * Signature: (J[JZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_Menu_nativeRefill
(JNIEnv *env, jobject peer, jlong menuObj, jlongArray newItems, jboolean onAppKit)
{
    JNI_COCOA_ENTER();

    // 1. create copy of array
    jsize length = 0;
    long * newItemsPtrs = NULL;
    if (newItems != NULL) {
        length = (*env)->GetArrayLength(env, newItems);
        if (length > 0) {
            size_t lengthInBytes = length * sizeof(long);
            newItemsPtrs = (long *) malloc(lengthInBytes);
            jlong *ptrs = (*env)->GetLongArrayElements(env, newItems, NULL);
            memcpy(newItemsPtrs, ptrs, lengthInBytes);
            (*env)->ReleaseLongArrayElements(env, newItems, ptrs, 0);

            // 2. retain new items
            for (int i = 0; i < length; i++) {
                id newItem = (id) (newItemsPtrs[i]);
                if (newItem != NULL) {
                    [newItem retain];
                }
            }
        }
    }

    // 3. schedule on AppKit
    Menu * __strong menu = (Menu *)menuObj;
    dispatch_block_t block = ^{
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
            id appMenu = [mainMenu numberOfItems] > 0 ? [mainMenu itemAtIndex:0] : nil;
            if (appMenu != nil) {
                [appMenu retain];
                [mainMenu removeAllItems];
                [mainMenu addItem:appMenu];
                [appMenu release];
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
            free(newItemsPtrs);
        }

        // remove first item (that wasn't removed before adding)
        if (countBefore > 0) {
            [menu->nsMenu removeItemAtIndex:0];
        }
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


#import "MenuItem.h"
#import "Menu.h"

#import "CustomMenuItemView.h"

#import "java_awt_event_KeyEvent.h"
#import "java_awt_event_InputEvent.h"

static JavaVM *g_jvm = NULL;
static jclass sjc_MenuItem = NULL;

void initGlobalVMPtr(JNIEnv * env) {
    if (g_jvm == NULL) {
        (*env)->GetJavaVM(env, &g_jvm);
    }
}

// must be called only from AppKit
JNIEnv * getAppKitEnv() {
    static JNIEnv *g_appKitEnv = NULL;
    if (g_jvm == NULL) {
        fprintf(stderr, "ERROR: cat't obtain JNIEnv because g_jvm wasn't initialized\n");
        return NULL;
    }
    if (g_appKitEnv == NULL) {
        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_4;
        args.name = "AppKit Thread";
        args.group = NULL;
        (*g_jvm)->AttachCurrentThreadAsDaemon(g_jvm, (void**)&g_appKitEnv, &args);
    }
    return g_appKitEnv;
}

// returns autorelease string
NSString* JavaStringToNSString(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) {
        return NULL;
    }
    jsize len = (*env)->GetStringLength(env, jstr);
    const jchar *chars = (*env)->GetStringChars(env, jstr, NULL);
    if (chars == NULL) {
        return NULL;
    }
    NSString *result = [NSString stringWithCharacters:(UniChar *)chars length:len];
    (*env)->ReleaseStringChars(env, jstr, chars);
    return result;
}

@implementation MenuComponent

-(id) initWithPeer:(jobject)peer {
    self = [super init];
    if (self) {
        // the peer has been made clobal ref before
        javaPeer = peer;
    }
    return self;
}

@end

@implementation MenuItem

- (id) initWithPeer:(jobject)peer asSeparator: (BOOL) asSeparator{
    self = [super initWithPeer:peer];
    if (self) {
        if (asSeparator) {
            nsMenuItem = (NSMenuItem*)[NSMenuItem separatorItem]; // creates autorelease
            [nsMenuItem retain];
        } else {
            nsMenuItem = [[NSMenuItem alloc] init];
            [nsMenuItem setAction:@selector(handleAction:)];
            [nsMenuItem setTarget:self];
        }
    }
    return self;
}

// Called from AppKit thread
- (void) handleAction:(NSMenuItem *)sender {
    JNI_COCOA_ENTER();

    JNIEnv *env = getAppKitEnv();
    GET_CLASS(sjc_MenuItem, "com/intellij/ui/mac/screenmenu/MenuItem");
    DECLARE_METHOD(jm_handleAction, sjc_MenuItem, "handleAction", "(I)V");

    NSEvent *currEvent = [[NSApplication sharedApplication] currentEvent];
    NSUInteger modifiers = [currEvent modifierFlags];
    jint jmodifiers = modifiers;
    (*env)->CallVoidMethod(env, javaPeer, jm_handleAction, jmodifiers);
    CHECK_EXCEPTION(env);
    JNI_COCOA_EXIT();
}

- (void) setAcceleratorText:(NSString *)acceleratorText {
    if ([@"" isEqualToString:acceleratorText]) {
        acceleratorText = nil;
    }
    CustomMenuItemView *menuItemView = (CustomMenuItemView *)nsMenuItem.view;
    if (menuItemView == nil) {
        nsMenuItem.view = menuItemView = [[[CustomMenuItemView alloc] initWithOwner:self] autorelease];
    }
    menuItemView.keyShortcut = acceleratorText;
    [menuItemView recalcSizes];
}

static struct _nsKeyToJavaModifier
{
    NSUInteger nsMask;
    //NSUInteger cgsLeftMask;
    //NSUInteger cgsRightMask;
    unsigned short leftKeyCode;
    unsigned short rightKeyCode;
    BOOL leftKeyPressed;
    BOOL rightKeyPressed;
    jint javaExtMask;
    jint javaMask;
    jint javaKey;
}
nsKeyToJavaModifierTable[] =
{
    {
        NSAlphaShiftKeyMask,
                0,
                0,
                NO,
                NO,
                0, // no Java equivalent
                0, // no Java equivalent
                java_awt_event_KeyEvent_VK_CAPS_LOCK
    },
    {
        NSShiftKeyMask,
                //kCGSFlagsMaskAppleShiftKey,
                //kCGSFlagsMaskAppleRightShiftKey,
                56,
                60,
                NO,
                NO,
                java_awt_event_InputEvent_SHIFT_DOWN_MASK,
                java_awt_event_InputEvent_SHIFT_MASK,
                java_awt_event_KeyEvent_VK_SHIFT
    },
    {
        NSControlKeyMask,
                //kCGSFlagsMaskAppleControlKey,
                //kCGSFlagsMaskAppleRightControlKey,
                59,
                62,
                NO,
                NO,
                java_awt_event_InputEvent_CTRL_DOWN_MASK,
                java_awt_event_InputEvent_CTRL_MASK,
                java_awt_event_KeyEvent_VK_CONTROL
    },
    {
        NSCommandKeyMask,
                //kCGSFlagsMaskAppleLeftCommandKey,
                //kCGSFlagsMaskAppleRightCommandKey,
                55,
                54,
                NO,
                NO,
                java_awt_event_InputEvent_META_DOWN_MASK,
                java_awt_event_InputEvent_META_MASK,
                java_awt_event_KeyEvent_VK_META
    },
    {
        NSAlternateKeyMask,
                //kCGSFlagsMaskAppleLeftAlternateKey,
                //kCGSFlagsMaskAppleRightAlternateKey,
                58,
                61,
                NO,
                NO,
                java_awt_event_InputEvent_ALT_DOWN_MASK,
                java_awt_event_InputEvent_ALT_MASK,
                java_awt_event_KeyEvent_VK_ALT
    },
    // NSNumericPadKeyMask
    {
        NSHelpKeyMask,
                0,
                0,
                NO,
                NO,
                0, // no Java equivalent
                0, // no Java equivalent
                java_awt_event_KeyEvent_VK_HELP
    },
    // NSFunctionKeyMask
    {0, 0, 0, NO, NO, 0, 0, 0}
};

// returns NSEventModifierFlags
NSUInteger JavaModifiersToNsKeyModifiers(jint javaModifiers, BOOL isExtMods)
{
    NSUInteger nsFlags = 0;
    struct _nsKeyToJavaModifier* cur;

    for (cur = nsKeyToJavaModifierTable; cur->nsMask != 0; ++cur) {
        jint mask = isExtMods? cur->javaExtMask : cur->javaMask;
        if ((mask & javaModifiers) != 0) {
            nsFlags |= cur->nsMask;
        }
    }
    return nsFlags;
}

- (void) setLabel:(NSString *)theLabel shortcut:(NSString *)theKeyEquivalent modifierMask:(jint)modifiers {
    NSUInteger modifierMask = 0;
    if (![theKeyEquivalent isEqualToString:@""]) {
        // Force the key equivalent to lower case if not using the shift key.
        // Otherwise AppKit will draw a Shift glyph in the menu.
        if ((modifiers & java_awt_event_KeyEvent_SHIFT_MASK) == 0) {
            theKeyEquivalent = [theKeyEquivalent lowercaseString];
        }

        // Hack for the question mark -- SHIFT and / means use the question mark.
        if ((modifiers & java_awt_event_KeyEvent_SHIFT_MASK) != 0 &&
            [theKeyEquivalent isEqualToString:@"/"])
        {
            theKeyEquivalent = @"?";
            modifiers &= ~java_awt_event_KeyEvent_SHIFT_MASK;
        }

        modifierMask = JavaModifiersToNsKeyModifiers(modifiers, NO);
    }

    [nsMenuItem setKeyEquivalent:theKeyEquivalent];
    [nsMenuItem setKeyEquivalentModifierMask:modifierMask];
    [nsMenuItem setTitle:theLabel];
    if ([nsMenuItem.view isKindOfClass:CustomMenuItemView.class]) {
        [(CustomMenuItemView *)nsMenuItem.view recalcSizes];
    }
}

- (void) setImage:(NSImage *)theImage {
    [nsMenuItem setImage:theImage];
    if ([nsMenuItem.view isKindOfClass:CustomMenuItemView.class]) {
        [(CustomMenuItemView *)nsMenuItem.view recalcSizes];
    }
}

- (void) dealloc {
    [nsMenuItem setAction:NULL];
    [nsMenuItem setTarget:nil];
    [nsMenuItem release];
    nsMenuItem = nil;
    [super dealloc];
}

- (NSString *) description {
    return [NSString stringWithFormat:@"MenuItem[ %@ ]", nsMenuItem];
}

@end


/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeCreate
 * Signature: (Z)J
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeCreate
(JNIEnv *env, jobject peer, jboolean isSeparator)
{
    initGlobalVMPtr(env);

    if (sjc_MenuItem == NULL) {
        // Cache MenuItem jclass, because JNI can't find it when:
        // 1. class in signed JAR
        // 2. class requested in AppKit-thread
        jclass peerClass = (*env)->GetObjectClass(env, peer);
        sjc_MenuItem = peerClass;
        if (sjc_MenuItem != NULL) {
            sjc_MenuItem = (*env)->NewGlobalRef(env, sjc_MenuItem);
        }
    }

    JNI_COCOA_ENTER();
    jobject javaPeer = (*env)->NewGlobalRef(env, peer);
    const BOOL asSeparator = (isSeparator == JNI_TRUE) ? YES: NO;
    // NOTE: returns retained MenuItem
    // Java owner must release it manually via nativeDispose (see MenuItem.dispose())
    MenuItem * menuItem = [[MenuItem alloc] initWithPeer:javaPeer asSeparator: asSeparator];
    // Set non empty title, just for insurance (that item won't be considered as empty and will be displayed)
    [menuItem->nsMenuItem setTitle:@" "];
    return (jlong)menuItem;
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeDispose
 * Signature: (J)V
 */
JNIEXPORT jlong JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeDispose
(JNIEnv *env, jobject peer, jlong menuItemObj)
{
    JNI_COCOA_ENTER();
    MenuItem *item = (MenuItem *)menuItemObj;
    (*env)->DeleteGlobalRef(env, item->javaPeer);
    item->javaPeer = NULL;
    [item release];
    JNI_COCOA_EXIT();
}

static unichar AWTKeyToMacShortcut(jint awtKey, BOOL doShift) {
    unichar macKey = 0;

    if ((awtKey >= java_awt_event_KeyEvent_VK_0 && awtKey <= java_awt_event_KeyEvent_VK_9) ||
        (awtKey >= java_awt_event_KeyEvent_VK_A && awtKey <= java_awt_event_KeyEvent_VK_Z))
    {
        // These ranges are the same in ASCII
        macKey = awtKey;
    } else if (awtKey >= java_awt_event_KeyEvent_VK_F1 && awtKey <= java_awt_event_KeyEvent_VK_F12) {
        // Support for F1 - F12 has been around since Java 1.0 and fall into a lower range.
        macKey = awtKey - java_awt_event_KeyEvent_VK_F1 + NSF1FunctionKey;
    } else if (awtKey >= java_awt_event_KeyEvent_VK_F13 && awtKey <= java_awt_event_KeyEvent_VK_F24) {
        // Support for F13-F24 came in Java 1.2 and are at a different range.
        macKey = awtKey - java_awt_event_KeyEvent_VK_F13 + NSF13FunctionKey;
    } else {
        // Special characters
        switch (awtKey) {
            case java_awt_event_KeyEvent_VK_BACK_QUOTE      : macKey = '`'; break;
            case java_awt_event_KeyEvent_VK_QUOTE           : macKey = '\''; break;

            case java_awt_event_KeyEvent_VK_ESCAPE          : macKey = 0x1B; break;
            case java_awt_event_KeyEvent_VK_SPACE           : macKey = ' '; break;
            case java_awt_event_KeyEvent_VK_PAGE_UP         : macKey = NSPageUpFunctionKey; break;
            case java_awt_event_KeyEvent_VK_PAGE_DOWN       : macKey = NSPageDownFunctionKey; break;
            case java_awt_event_KeyEvent_VK_END             : macKey = NSEndFunctionKey; break;
            case java_awt_event_KeyEvent_VK_HOME            : macKey = NSHomeFunctionKey; break;

            case java_awt_event_KeyEvent_VK_LEFT            : macKey = NSLeftArrowFunctionKey; break;
            case java_awt_event_KeyEvent_VK_UP              : macKey = NSUpArrowFunctionKey; break;
            case java_awt_event_KeyEvent_VK_RIGHT           : macKey = NSRightArrowFunctionKey; break;
            case java_awt_event_KeyEvent_VK_DOWN            : macKey = NSDownArrowFunctionKey; break;

            case java_awt_event_KeyEvent_VK_COMMA           : macKey = ','; break;

                // Mac OS doesn't distinguish between the two '-' keys...
            case java_awt_event_KeyEvent_VK_MINUS           :
            case java_awt_event_KeyEvent_VK_SUBTRACT        : macKey = '-'; break;

                // or the two '.' keys...
            case java_awt_event_KeyEvent_VK_DECIMAL         :
            case java_awt_event_KeyEvent_VK_PERIOD          : macKey = '.'; break;

                // or the two '/' keys.
            case java_awt_event_KeyEvent_VK_DIVIDE          :
            case java_awt_event_KeyEvent_VK_SLASH           : macKey = '/'; break;

            case java_awt_event_KeyEvent_VK_SEMICOLON       : macKey = ';'; break;
            case java_awt_event_KeyEvent_VK_EQUALS          : macKey = '='; break;

            case java_awt_event_KeyEvent_VK_OPEN_BRACKET    : macKey = '['; break;
            case java_awt_event_KeyEvent_VK_BACK_SLASH      : macKey = '\\'; break;
            case java_awt_event_KeyEvent_VK_CLOSE_BRACKET   : macKey = ']'; break;

            case java_awt_event_KeyEvent_VK_MULTIPLY        : macKey = '*'; break;
            case java_awt_event_KeyEvent_VK_ADD             : macKey = '+'; break;

            case java_awt_event_KeyEvent_VK_HELP            : macKey = NSHelpFunctionKey; break;
            case java_awt_event_KeyEvent_VK_TAB             : macKey = NSTabCharacter; break;
            case java_awt_event_KeyEvent_VK_ENTER           : macKey = NSNewlineCharacter; break;
            case java_awt_event_KeyEvent_VK_BACK_SPACE      : macKey = NSBackspaceCharacter; break;
            case java_awt_event_KeyEvent_VK_DELETE          : macKey = NSDeleteCharacter; break;
            case java_awt_event_KeyEvent_VK_CLEAR           : macKey = NSClearDisplayFunctionKey; break;
            case java_awt_event_KeyEvent_VK_AMPERSAND       : macKey = '&'; break;
            case java_awt_event_KeyEvent_VK_ASTERISK        : macKey = '*'; break;
            case java_awt_event_KeyEvent_VK_QUOTEDBL        : macKey = '\"'; break;
            case java_awt_event_KeyEvent_VK_LESS            : macKey = '<'; break;
            case java_awt_event_KeyEvent_VK_GREATER         : macKey = '>'; break;
            case java_awt_event_KeyEvent_VK_BRACELEFT       : macKey = '{'; break;
            case java_awt_event_KeyEvent_VK_BRACERIGHT      : macKey = '}'; break;
            case java_awt_event_KeyEvent_VK_AT              : macKey = '@'; break;
            case java_awt_event_KeyEvent_VK_COLON           : macKey = ':'; break;
            case java_awt_event_KeyEvent_VK_CIRCUMFLEX      : macKey = '^'; break;
            case java_awt_event_KeyEvent_VK_DOLLAR          : macKey = '$'; break;
            case java_awt_event_KeyEvent_VK_EXCLAMATION_MARK : macKey = '!'; break;
            case java_awt_event_KeyEvent_VK_LEFT_PARENTHESIS : macKey = '('; break;
            case java_awt_event_KeyEvent_VK_NUMBER_SIGN     : macKey = '#'; break;
            case java_awt_event_KeyEvent_VK_PLUS            : macKey = '+'; break;
            case java_awt_event_KeyEvent_VK_RIGHT_PARENTHESIS: macKey = ')'; break;
            case java_awt_event_KeyEvent_VK_UNDERSCORE      : macKey = '_'; break;
        }
    }
    return macKey;
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetLabel
 * Signature: (JLjava/lang/String;CII)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetLabel
(JNIEnv *env, jobject peer, jlong menuItemObj, jstring label, jchar shortcutKey, jint shortcutKeyCode, jint mods)
{
    JNI_COCOA_ENTER();
    NSString *theLabel = JavaStringToNSString(env, label);
    NSString *theKeyEquivalent = nil;
    unichar macKey = shortcutKey;

    if (macKey == 0) {
        macKey = AWTKeyToMacShortcut(shortcutKeyCode, (mods & java_awt_event_KeyEvent_SHIFT_MASK) != 0);
    }

    if (macKey != 0) {
        unichar equivalent[1] = {macKey};
        theKeyEquivalent = [NSString stringWithCharacters:equivalent length:1];
    } else {
        theKeyEquivalent = @"";
    }

    [((MenuItem *) menuItemObj) setLabel:theLabel shortcut:theKeyEquivalent modifierMask:mods];
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetAcceleratorText
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetAcceleratorText
(JNIEnv *env, jobject peer, jlong menuItemObj, jstring acceleratorText)
{
    JNI_COCOA_ENTER();
    NSString *theText = JavaStringToNSString(env, acceleratorText);
    [((MenuItem *)menuItemObj) setAcceleratorText:theText];
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetImage
 * Signature: (J[III)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetImage
(JNIEnv *env, jobject peer, jlong menuItemObj, jintArray buffer, jint width, jint height)
{
    JNI_COCOA_ENTER();
    NSImage *nsImage = nil;
    if (width > 0 && height > 0) {
        // 1. prepare NSImage
        NSBitmapImageRep *imageRep = [[[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL
                                                                              pixelsWide:width
                                                                              pixelsHigh:height
                                                                           bitsPerSample:8
                                                                         samplesPerPixel:4
                                                                                hasAlpha:YES
                                                                                isPlanar:NO
                                                                          colorSpaceName:NSDeviceRGBColorSpace
                                                                            bitmapFormat:NSAlphaFirstBitmapFormat
                                                                             bytesPerRow:width * 4
                                                                            bitsPerPixel:32] autorelease];

        jint *imgData = (jint *) [imageRep bitmapData];
        jint *src = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                jint pix = src[x];
                jint a = (pix >> 24) & 0xff;
                jint r = (pix >> 16) & 0xff;
                jint g = (pix >> 8) & 0xff;
                jint b = (pix) & 0xff;
                imgData[x] = (b << 24) | (g << 16) | (r << 8) | a;
            }
            src += width;
            imgData += width;
        }

        (*env)->ReleasePrimitiveArrayCritical(env, buffer, src, JNI_ABORT);

        nsImage = [[NSImage alloc] initWithSize:NSMakeSize(width, height)];
        [nsImage addRepresentation:imageRep];
    }

    // 2. set image for item
    [((MenuItem *)menuItemObj) setImage:nsImage];
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetEnabled
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetEnabled
(JNIEnv *env, jobject peer, jlong menuItemObj, jboolean enable)
{
    JNI_COCOA_ENTER();
    MenuItem *item = (MenuItem *)menuItemObj;
    [item->nsMenuItem setEnabled:(enable == JNI_TRUE)];
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetSubmenu
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetSubmenu
(JNIEnv *env, jobject peer, jlong menuItemObj, jlong submenuObj)
{
    JNI_COCOA_ENTER();
    MenuItem *item = (MenuItem *)menuItemObj;
    Menu * submenu = (Menu *)submenuObj;
    [item->nsMenuItem setSubmenu:submenu->nsMenu];
    JNI_COCOA_EXIT();
}

/*
 * Class:     com_intellij_ui_mac_screenmenu_MenuItem
 * Method:    nativeSetState
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_intellij_ui_mac_screenmenu_MenuItem_nativeSetState
        (JNIEnv *env, jobject peer, jlong menuItemObj, jboolean isToggled)
{
    JNI_COCOA_ENTER();
    MenuItem *item = (MenuItem *)menuItemObj;
    [item->nsMenuItem setState:(isToggled == JNI_TRUE ? NSOnState : NSOffState)];
    JNI_COCOA_EXIT();
}

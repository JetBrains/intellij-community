#ifndef MACSCREENMENU_MENUITEM_H
#define MACSCREENMENU_MENUITEM_H

#import <Cocoa/Cocoa.h>
#import <jni.h>

@interface MenuComponent : NSObject {
@public
    jobject javaPeer;
}

- (id) initWithPeer:(jobject)peer;
@end


@interface MenuItem : MenuComponent {
    bool isAttached;
@public
    NSMenuItem * nsMenuItem;
}

- (id) initWithPeer:(jobject)peer asSeparator: (BOOL) asSeparator;

- (void) setLabel:(NSString *)theLabel
         shortcut:(NSString *)theKeyEquivalent
     modifierMask:(jint)modifiers;
- (void) setAcceleratorText:(NSString *)acceleratorText;
- (void) setImage:(NSImage *)theImage;

- (void) handleAction:(NSMenuItem *) sender;
@end

//
// JNI utils
//

JNIEnv * getAppKitEnv();
NSString* JavaStringToNSString(JNIEnv *env, jstring jstr);
void initGlobalVMPtr(JNIEnv * env);

#define JNI_COCOA_ENTER() \
 NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init]; \
 @try {

#define JNI_COCOA_EXIT() \
 } \
 @catch (NSException *e) { \
     NSLog(@"%@", [e callStackSymbols]); \
 } \
 @finally { \
    [pool drain]; \
 };

#define GET_CLASS(dst_var, cls) \
     if (dst_var == NULL) { \
         dst_var = (*env)->FindClass(env, cls); \
         if (dst_var != NULL) dst_var = (*env)->NewGlobalRef(env, dst_var); \
     } \
     if (dst_var == NULL) { \
         fprintf(stderr, "Can't find class %s\n", cls); \
     } \
     if ((dst_var) == NULL) \
         return;

#define DECLARE_CLASS(dst_var, cls) \
    static jclass dst_var = NULL; \
    GET_CLASS(dst_var, cls);

#define GET_METHOD(dst_var, cls, name, signature) \
     if (dst_var == NULL) { \
         dst_var = (*env)->GetMethodID(env, cls, name, signature); \
     } \
     if (dst_var == NULL) { \
         fprintf(stderr, "Can't find method %s\n", name); \
     } \
     if ((dst_var) == NULL) \
         return;

#define DECLARE_METHOD(dst_var, cls, name, signature) \
     static jmethodID dst_var = NULL; \
     GET_METHOD(dst_var, cls, name, signature);

#define CHECK_EXCEPTION(env) { \
    jthrowable exc = (*(env))->ExceptionOccurred(env); \
    if (exc != NULL) { \
        if ([NSThread isMainThread] == YES) { \
            (*(env))->ExceptionClear(env); \
        } \
        (*(env))->ExceptionClear(env); \
    } \
};

#endif //MACSCREENMENU_MENUITEM_H

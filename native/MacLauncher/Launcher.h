//
// Created by max on 5/4/12.
//
// To change the template use AppCode | Preferences | File Templates.
//


#import <Foundation/Foundation.h>
#import <JavaNativeFoundation/jnf_fallback_jni.h>


@interface Launcher : NSObject {
    int argc;
    char **argv;
}
- (id)initWithArgc:(int)anArgc argv:(char **)anArgv;

BOOL validationJavaVersion();

- (void) launch;
@end

NSString *getExecutable();
NSString *jvmVersion(NSBundle *bundle);
NSString *requiredJvmVersions();
NSString *getPropertiesFilePath();
NSString *getPreferencesFolderPath();
BOOL meetMinRequirements(NSString *vmVersion);
BOOL satisfies(NSString *vmVersion, NSString *requiredVersion);
typedef jint (JNICALL *fun_ptr_t_CreateJavaVM)(JavaVM **pvm, JNIEnv **env, void *args);

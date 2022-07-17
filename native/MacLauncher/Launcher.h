// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import <Foundation/Foundation.h>
#import <jni.h>


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

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#import <Foundation/Foundation.h>
#import <jni.h>


@interface Launcher : NSObject {
    int argc;
    char **argv;
}
- (id)initWithArgc:(int)anArgc argv:(char **)anArgv;

bool validationJavaVersion(void);

- (void) launch;
@end

NSString *getExecutable(void);
NSString *jvmVersion(NSBundle *bundle);
NSString *requiredJvmVersions(void);
NSString *getPropertiesFilePath(void);
NSString *getPreferencesFolderPath(void);
bool meetMinRequirements(NSString *vmVersion);
bool satisfies(NSString *vmVersion, NSString *requiredVersion);
typedef jint (JNICALL *fun_ptr_t_CreateJavaVM)(JavaVM **pvm, JNIEnv **env, void *args);

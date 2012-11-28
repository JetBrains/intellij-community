//
// Created by max on 5/4/12.
//
// To change the template use AppCode | Preferences | File Templates.
//


#import "Launcher.h"
#import "VMOptionsReader.h"
#import "PropertyFileReader.h"
#import "utils.h"
#import <dlfcn.h>

typedef jint (JNICALL *fun_ptr_t_CreateJavaVM)(JavaVM **pvm, void **env, void *args);


@implementation NSString (CustomReplacements)
- (NSString *)replaceAll:(NSString *)pattern to:(NSString *)replacement {
    if ([self rangeOfString:pattern].length == 0) return self;

    NSMutableString *answer = [self mutableCopy];
    [answer replaceOccurrencesOfString:pattern withString:replacement options:0 range:NSMakeRange(0, [self length])];
    return answer;
}
@end

@implementation NSDictionary (TypedGetters)
- (NSDictionary *)dictionaryForKey:(id)key {
    id answer = [self objectForKey:key];
    if ([answer isKindOfClass:[NSDictionary class]]) {
        return answer;
    }
    return nil;
}

- (id)valueForKey:(NSString *)key inDictionary:(NSString *)dictKey default: (NSString*) defaultValue {
    NSDictionary *dict = [self dictionaryForKey:dictKey];
    if (dict == nil) return nil;
    id answer = [dict valueForKey:key];
    return answer != nil ? answer : defaultValue;
}
@end

@implementation Launcher {
    int argc;
    char **argv;
}

- (id)initWithArgc:(int)anArgc argv:(char **)anArgv {
    self = [super init];
    if (self) {
        argc = anArgc;
        argv = anArgv;
    }

    return self;
}


void appendJvmBundlesAt(NSString *path, NSMutableArray *sink) {
    NSError *error;
    NSArray *names = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:path error:&error];

    if (!error) {
        for (NSString *name in names) {
            if ([name hasSuffix:@".jdk"] || [name hasSuffix:@".jre"]) {
                NSBundle *bundle = [NSBundle bundleWithPath:[path stringByAppendingPathComponent:name]];
                if (bundle != nil) {
                    [sink addObject:bundle];
                }
            }
        }
    }
}

NSArray *allVms() {
    NSMutableArray *jvmBundlePaths = [NSMutableArray array];

    NSString *explicit = [[[NSProcessInfo processInfo] environment] objectForKey:@"IDEA_JDK"];

    if (explicit != nil) {
        appendJvmBundlesAt(explicit, jvmBundlePaths);
    }
    else {
        NSBundle *bundle = [NSBundle mainBundle];
        NSString *appDir = bundle.bundlePath;

        appendJvmBundlesAt([appDir stringByAppendingPathComponent:@"jre"], jvmBundlePaths);
        if (jvmBundlePaths.count > 0) return jvmBundlePaths;

        appendJvmBundlesAt([NSHomeDirectory() stringByAppendingPathComponent:@"Library/Java/JavaVirtualMachines"], jvmBundlePaths);
        appendJvmBundlesAt(@"/Library/Java/JavaVirtualMachines", jvmBundlePaths);
        appendJvmBundlesAt(@"/System/Library/Java/JavaVirtualMachines", jvmBundlePaths);
    }

    return jvmBundlePaths;
}

NSString *jvmVersion(NSBundle *bundle) {
    return [bundle.infoDictionary valueForKey:@"JVMVersion" inDictionary:@"JavaVM" default: @"0"];
}

NSString *requiredJvmVersion() {
    return [[NSBundle mainBundle].infoDictionary valueForKey:@"JVMVersion" inDictionary:@"Java" default: @"1.7*"];
}

BOOL satisfies(NSString *vmVersion, NSString *requiredVersion) {
    if ([requiredVersion hasSuffix:@"+"]) {
        requiredVersion = [requiredVersion substringToIndex:[requiredVersion length] - 1];
        return [requiredVersion compare:vmVersion options:NSNumericSearch] <= 0;
    }

    if ([requiredVersion hasSuffix:@"*"]) {
        requiredVersion = [requiredVersion substringToIndex:[requiredVersion length] - 1];
    }

    return [vmVersion hasPrefix:requiredVersion];
}

NSBundle *findMatchingVm() {
    NSArray *vmBundles = [allVms() sortedArrayUsingComparator:^NSComparisonResult(id b1, id b2) {
        return [jvmVersion(b2) compare:jvmVersion(b1) options:NSNumericSearch];
    }];

    if (isDebugEnabled()) {
        debugLog(@"Found Java Virtual Machines:");
        for (NSBundle *vm in vmBundles) {
            debugLog([vm bundlePath]);
        }
    }

    NSString *required = requiredJvmVersion();
    debugLog([NSString stringWithFormat:@"Required VM: %@", required]);

    for (NSBundle *vm in vmBundles) {
        if (satisfies(jvmVersion(vm), required)) {
            debugLog(@"Chosen VM:");
            debugLog([vm bundlePath]);
            return vm;
        }
    }

    debugLog(@"No matching VM found");

    return nil;
}

CFBundleRef NSBundle2CFBundle(NSBundle *bundle) {
    CFURLRef bundleURL = (__bridge CFURLRef) bundle.bundleURL;
    return CFBundleCreate(kCFAllocatorDefault, bundleURL);
}

- (NSString *)expandMacros:(NSString *)str {
    return [[str
            replaceAll:@"$APP_PACKAGE" to:[[NSBundle mainBundle] bundlePath]]
            replaceAll:@"$USER_HOME" to:NSHomeDirectory()];
}

- (NSMutableString *)buildClasspath:(NSBundle *)jvm {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"Java"];
    NSMutableString *classpathOption = [NSMutableString stringWithString:@"-Djava.class.path="];
    [classpathOption appendString:[jvmInfo objectForKey:@"ClassPath"]];

    NSString *toolsJar = [[jvm bundlePath] stringByAppendingString:@"/Contents/Home/lib/tools.jar"];
    if ([[NSFileManager defaultManager] fileExistsAtPath:toolsJar]) {
        [classpathOption appendString:@":"];
        [classpathOption appendString:toolsJar];
    }
    return classpathOption;
}


NSString *getSelector() {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"Java"];
    NSDictionary *properties = [jvmInfo dictionaryForKey:@"Properties"];
    if (properties != nil) {
        return [properties objectForKey:@"idea.paths.selector"];
    }
    return nil;
}

NSString *getPreferencesFolderPath() {
    return [NSString stringWithFormat:@"%@/Library/Preferences/%@", NSHomeDirectory(), getSelector()];
}

NSString *getPropertiesFilePath() {
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.properites"];
}

NSString *getDefaultPropertiesFilePath() {
    return [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@"/bin/idea.properties"];
}

NSString *getDefaultVMOptionsFilePath() {
    return [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@"/bin/idea.vmoptions"];
}

NSString *getVMOptionsFilePath() {
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.vmoptions"];
}

NSArray *parseVMOptions() {
    NSArray *inConfig=[VMOptionsReader readFile:getVMOptionsFilePath()];
    if (inConfig) return inConfig;
    return [VMOptionsReader readFile:getDefaultVMOptionsFilePath()];
}

NSDictionary *parseProperties() {
    NSDictionary *inConfig = [PropertyFileReader readFile:getPropertiesFilePath()];
    if (inConfig) return inConfig;
    return [PropertyFileReader readFile:getDefaultPropertiesFilePath()];
}

- (JavaVMInitArgs)buildArgsFor:(NSBundle *)jvm {
    NSMutableString *classpathOption = [self buildClasspath:jvm];

    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"Java"];
    NSMutableArray *args_array = [NSMutableArray array];

    [args_array addObject:classpathOption];

    [args_array addObjectsFromArray:[[jvmInfo objectForKey:@"VMOptions"] componentsSeparatedByString:@" "]];
    [args_array addObjectsFromArray:parseVMOptions()];    

    void (^propertySink)(id,id,BOOL *)=^(id key, id obj, BOOL *stop) {
        [args_array addObject:[NSString stringWithFormat:@"-D%@=%@", key, obj]];
    };

    NSDictionary *properties = [jvmInfo dictionaryForKey:@"Properties"];
    if (properties != nil) {
        [properties enumerateKeysAndObjectsUsingBlock:propertySink];
    }

    [parseProperties() enumerateKeysAndObjectsUsingBlock:propertySink];

    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_6;
    args.ignoreUnrecognized = JNI_TRUE;

    args.nOptions = [args_array count];
    args.options = calloc((size_t) args.nOptions, sizeof(JavaVMOption));
    [args_array enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
        args.options[idx].optionString = strdup([[self expandMacros:[obj description]] UTF8String]);
    }];
    return args;
}

- (const char *)mainClassName {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"Java"];
    char *answer = strdup([[jvmInfo objectForKey:@"MainClass"] UTF8String]);
    
    char *cur = answer;
    while (*cur) {
        if (*cur == '.') {
            *cur = '/';
        }
        cur++;
    }
    
    return answer;
}

- (void)process_cwd {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"Java"];
    NSString *cwd = [jvmInfo objectForKey:@"WorkingDirectory"];
    if (cwd != nil) {
        cwd = [self expandMacros:cwd];
        if (chdir([cwd UTF8String]) != 0) {
            NSLog(@"Cannot chdir to working directory at %@", cwd);
        }
    }
}

- (void)launch {
    NSBundle *vm = findMatchingVm();
    if (vm == nil) {
        NSString *old_launcher = [self expandMacros:@"$APP_PACKAGE/Contents/MacOS/idea_appLauncher"];
        execv([old_launcher fileSystemRepresentation], self->argv);

        NSLog(@"Cannot find matching VM, aborting");
        exit(-1);
    }

    NSError *error;
    BOOL ok = [vm loadAndReturnError:&error];
    if (!ok || error != nil) {
        NSLog(@"Cannot load JVM bundle: %@", error);
        exit(-1);
    }

    CFBundleRef cfvm = NSBundle2CFBundle(vm);

    fun_ptr_t_CreateJavaVM create_vm = CFBundleGetFunctionPointerForName(cfvm, CFSTR("JNI_CreateJavaVM"));

    if (create_vm == NULL) {
        // We have Apple VM chosen here...
/*
        [self execCommandLineJava:vm];
        return;
*/

        NSString *serverLibUrl = [vm.bundlePath stringByAppendingPathComponent:@"Contents/Libraries/libserver.dylib"];

        void *libHandle = dlopen(serverLibUrl.UTF8String, RTLD_NOW + RTLD_GLOBAL);
        if (libHandle) {
            create_vm = dlsym(libHandle, "JNI_CreateJavaVM_Impl");
        }
    }

    if (create_vm == NULL) {
        NSLog(@"Cannot find JNI_CreateJavaVM in chosen JVM bundle at %@", vm.bundlePath);
        exit(-1);
    }

    [self process_cwd];

    JNIEnv *env;
    JavaVM *jvm;

    JavaVMInitArgs args = [self buildArgsFor:vm];

    jint create_vm_rc = create_vm(&jvm, &env, &args);
    if (create_vm_rc != JNI_OK || jvm == NULL) {
        NSLog(@"JNI_CreateJavaVM (%@) failed: %d", vm.bundlePath, create_vm_rc);
        exit(-1);
    }

    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) {
        NSLog(@"No java.lang.String in classpath!");
        exit(-1);
    }

    const char *mainClassName = [self mainClassName];
    jclass mainClass = (*env)->FindClass(env, mainClassName);
    if (mainClass == NULL || (*env)->ExceptionOccurred(env)) {
        NSLog(@"Main class %s not found", mainClassName);
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }

    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL || (*env)->ExceptionOccurred(env)) {
        NSLog(@"Cant't find main() method");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }

    // See http://stackoverflow.com/questions/10242115/os-x-strange-psn-command-line-parameter-when-launched-from-finder
    // about psn_ stuff
    int arg_count = 0;
    for (int i = 1; i < argc; i++) {
        if (memcmp(argv[i], "-psn_", 4) != 0) arg_count++;
    }

    jobject jni_args = (*env)->NewObjectArray(env, arg_count, string_class, NULL);

    arg_count = 0;
    for (int i = 1; i < argc; i++) {
        if (memcmp(argv[i], "-psn_", 4) != 0) {
            jstring jni_arg = (*env)->NewStringUTF(env, argv[i]);
            (*env)->SetObjectArrayElement(env, jni_args, arg_count, jni_arg);
            arg_count++;
        }
    }

    (*env)->CallStaticVoidMethod(env, mainClass, mainMethod, jni_args);

    (*jvm)->DetachCurrentThread(jvm);
    (*jvm)->DestroyJavaVM(jvm);
}


@end
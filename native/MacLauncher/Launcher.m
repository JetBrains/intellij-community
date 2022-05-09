// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "Launcher.h"
#import "VMOptionsReader.h"
#import "PropertyFileReader.h"
#import "utils.h"
#import "rosetta.h"
#import <AppKit/AppKit.h>
#import <dlfcn.h>


NSBundle *vm;
NSString *const JVMOptions = @"JVMOptions";
NSString *JVMVersion = NULL;
NSString* minRequiredJavaVersion = @"1.8";

@interface NSString (CustomReplacements)
- (NSString *)replaceAll:(NSString *)pattern to:(NSString *)replacement;
@end

@implementation NSString (CustomReplacements)
- (NSString *)replaceAll:(NSString *)pattern to:(NSString *)replacement {
    if ([self rangeOfString:pattern].length == 0) return self;

    NSMutableString *answer = [[self mutableCopy] autorelease];
    [answer replaceOccurrencesOfString:pattern withString:replacement options:0 range:NSMakeRange(0, [self length])];
    return answer;
}
@end

@interface NSDictionary (TypedGetters)
- (NSDictionary *)dictionaryForKey:(id)key;
- (NSArray *)arrayForKey:(id)key;
- (id)valueForKey:(NSString *)key inDictionary:(NSString *)dictKey defaultObject:(NSString *)defaultValue;
@end

@implementation NSDictionary (TypedGetters)
- (NSDictionary *)dictionaryForKey:(id)key {
    id answer = self[key];
    return [answer isKindOfClass:[NSDictionary class]] ? answer : nil;
}

- (NSArray *)arrayForKey:(id)key {
    id answer = self[key];
    return [answer isKindOfClass:[NSArray class]] ? answer : nil;
}

- (id)valueForKey:(NSString *)key inDictionary:(NSString *)dictKey defaultObject: (NSString*) defaultValue {
    NSDictionary *dict = [self dictionaryForKey:dictKey];
    if (dict == nil) return nil;
    id answer = [dict valueForKey:key];
    return answer != nil ? answer : defaultValue;
}
@end

@implementation Launcher
- (id)initWithArgc:(int)anArgc argv:(char **)anArgv {
    self = [super init];
    if (self) {
        argc = anArgc;
        argv = anArgv;
    }

    return self;
}

void showWarning(NSString* messageText){
   NSAlert* alert = [[NSAlert alloc] init];
   [alert addButtonWithTitle:@"OK"];
   NSString* message_description = [NSString stringWithFormat:@"Java 1.8 or later is required."];
   NSString* informativeText =[NSString stringWithFormat:@"%@",message_description];
   [alert setMessageText:messageText];
   [alert setInformativeText:informativeText ];
   [alert setAlertStyle:NSAlertStyleWarning];
   [alert runModal];
   [alert release];
}

BOOL appendBundle(NSString *path, NSMutableArray *sink) {
    if (! [[NSFileManager defaultManager] fileExistsAtPath:path]) {
        NSLog(@"Can't find bundled java.The folder doesn't exist: %@", path);
    }
    else {
        if ([path hasSuffix:@"jdk"] || [path hasSuffix:@".jre"] || [path hasSuffix:@"jbr"]) {
            NSBundle *bundle = [NSBundle bundleWithPath:path];
            if (bundle != nil) {
                [sink addObject:bundle];
                return true;
            }
        }
    }
    return false;
}

BOOL appendJvmBundlesAt(NSString *path, NSMutableArray *sink) {
    NSError *error = nil;
    NSArray *names = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:path error:&error];

    BOOL res = false;
    if (names != nil) {
        for (NSString *name in names) {
            res |= appendBundle([path stringByAppendingPathComponent:name], sink);
        }
    }
    return res;
}

NSArray *allVms() {
    // search java info in user's idea.properties
    NSString* ideaProperty = getPropertiesFilePath();
    if ([[NSFileManager defaultManager] fileExistsAtPath:ideaProperty]) {
        NSDictionary *inConfig =[PropertyFileReader readFile:ideaProperty];
        NSString* userJavaVersion = inConfig[@"JVMVersion"];
        if (userJavaVersion != nil && meetMinRequirements(userJavaVersion)) {
            JVMVersion = userJavaVersion;
            NSLog(@"user JavaVersion from custom configs, which mentioned in idea.properties %@", userJavaVersion);
        }
    }

    NSString *required = requiredJvmVersions();
    NSLog(@"allVms required %@", required);

    NSMutableArray *jvmBundlePaths = [NSMutableArray array];

    NSBundle *bundle = [NSBundle mainBundle];
    appendBundle([bundle.bundlePath stringByAppendingPathComponent:@"Contents/jbr"], jvmBundlePaths);

    if (jvmBundlePaths.count == 0 || !satisfies(jvmVersion(jvmBundlePaths[jvmBundlePaths.count-1]), required)) {
        NSLog(@"Can't get bundled java version. It is probably corrupted.");

        appendJvmBundlesAt([NSHomeDirectory() stringByAppendingPathComponent:@"Library/Java/JavaVirtualMachines"], jvmBundlePaths);
        appendJvmBundlesAt(@"/Library/Java/JavaVirtualMachines", jvmBundlePaths);
        appendJvmBundlesAt(@"/System/Library/Java/JavaVirtualMachines", jvmBundlePaths);
    }

    return jvmBundlePaths;
}

NSString *jvmVersion(NSBundle *bundle) {
    NSString* javaVersion = [bundle.infoDictionary valueForKey:@"JVMVersion" inDictionary:@"JavaVM" defaultObject:@"0"];
    //NSLog(@"jvmVersion: %@", javaVersion);
    return javaVersion;
}

NSString *requiredJvmVersions() {
    return (JVMVersion != NULL) ? JVMVersion : [[NSBundle mainBundle].infoDictionary valueForKey:@"JVMVersion" inDictionary: JVMOptions defaultObject:@"1.8*"];
}

BOOL meetMinRequirements (NSString *vmVersion) {
    return [minRequiredJavaVersion compare:vmVersion options:NSNumericSearch] <= 0;
}

BOOL satisfies(NSString *vmVersion, NSString *requiredVersion) {
    BOOL meetRequirement = meetMinRequirements(vmVersion);
    if (! meetRequirement) {
        return meetRequirement;
    }

    if ([requiredVersion hasSuffix:@"+"]) {
        requiredVersion = [requiredVersion substringToIndex:[requiredVersion length] - 1];
        return [requiredVersion compare:vmVersion options:NSNumericSearch] <= 0;
    }
    if ([requiredVersion hasSuffix:@"*"]) {
        requiredVersion = [requiredVersion substringToIndex:[requiredVersion length] - 1];
    }
    return [vmVersion hasPrefix:requiredVersion];
}

NSComparisonResult compareVMVersions(id vm1, id vm2, __unused void *context) {
    return [jvmVersion(vm2) compare:jvmVersion(vm1) options:NSNumericSearch];
}

NSBundle *getJDKBundle(NSString* jdkVersion, NSString* source) {
    if (jdkVersion != nil) {
        NSBundle *jdkBundle = [NSBundle bundleWithPath : jdkVersion];
        if (jdkBundle != nil && ![jvmVersion(jdkBundle) isEqualToString :@"0"]) {
            NSString *javaVersion = jvmVersion(jdkBundle);
            if (javaVersion != nil && meetMinRequirements(javaVersion)) {
                debugLog(@"VM from:");
                debugLog(source);
                debugLog([jdkBundle bundlePath]);
                return jdkBundle;
            }
        }
    }
    return nil;
}

NSBundle *findMatchingVm() {
    // boot jdk action
    NSFileManager *fileManager = [NSFileManager defaultManager];

    NSString *pathForFile = [NSString stringWithFormat:@"%@/%@.jdk", getPreferencesFolderPath(), getExecutable()];

    if (!pathForFile.isAbsolutePath) {
        // Handle relative paths
        pathForFile = [[[NSBundle mainBundle] bundlePath] stringByAppendingPathComponent:pathForFile];
    }

    if ([fileManager fileExistsAtPath:pathForFile]) {
        NSString* fileContents = [NSString stringWithContentsOfFile:pathForFile encoding:NSUTF8StringEncoding error:nil];
        NSArray* allLinedStrings = [fileContents componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
        if (allLinedStrings.count > 0) {
            NSString* jdkFromProfile = allLinedStrings[0];
            NSBundle *jdkBundle = getJDKBundle(jdkFromProfile, @"IDE profile");
            if (jdkBundle != nil) {
                return jdkBundle;
            }
        }
    }

    //the environment variable.
    NSString *variable = [[getExecutable() uppercaseString] stringByAppendingString:@"_JDK"];
    // The explicitly set JDK to use.
    NSString *explicit = [[NSProcessInfo processInfo] environment][variable];
    if (explicit != nil) {
        NSLog(@"Value of %@: %@", variable, explicit);
        NSBundle *jdkBundle = getJDKBundle(explicit, @"environment variable");
        if (jdkBundle != nil) {
          return jdkBundle;
        }
        else {
          NSLog(@"Value of environment variable: %@ doesn't point to valid JDK: %@", variable, explicit);
        }
    }

    NSArray *vmBundles = [allVms() sortedArrayUsingFunction:compareVMVersions context:NULL];

    if (isDebugEnabled()) {
        debugLog(@"Found Java Virtual Machines:");
        for (NSBundle *vm in vmBundles) {
            debugLog([vm bundlePath]);
        }
    }

    NSString *requiredList = requiredJvmVersions();
    debugLog([NSString stringWithFormat:@"Required VMs: %@", requiredList]);

    if (requiredList != nil) {
        NSArray *array = [requiredList componentsSeparatedByString:@","];
        for (NSString* required in array) {
            for (NSBundle *vm in vmBundles) {
                if (satisfies(jvmVersion(vm), required)) {
                    debugLog(@"Chosen VM:");
                    debugLog([vm bundlePath]);
                    return vm;
                 }
            }
        }
    }
    else {
        NSLog(@"Info.plist is corrupted, Absent JVMOptions key.");
        exit(-1);
    }
    NSLog(@"No matching VM found.");
    showWarning(@"No matching VM found.");
    return nil;
}

CFBundleRef NSBundle2CFBundle(NSBundle *bundle) {
    CFURLRef bundleURL = (CFURLRef) ([NSURL fileURLWithPath:bundle.bundlePath]);
    return CFBundleCreate(kCFAllocatorDefault, bundleURL);
}

- (NSString *)expandMacros:(NSString *)str {
    return [[str
            replaceAll:@"$APP_PACKAGE" to:[[NSBundle mainBundle] bundlePath]]
            replaceAll:@"$USER_HOME" to:NSHomeDirectory()];
}

- (NSMutableString *)buildClasspath:(NSBundle *)jvm {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSMutableString *classpathOption = [NSMutableString stringWithString:@"-Djava.class.path="];
    NSString *classPath = jvmInfo[@"ClassPath"];
    if (classPath != nil) {
        [classpathOption appendString:jvmInfo[@"ClassPath"]];
        NSString *toolsJar = [[jvm bundlePath] stringByAppendingString:@"/Contents/Home/lib/tools.jar"];
        if ([[NSFileManager defaultManager] fileExistsAtPath:toolsJar]) {
            [classpathOption appendString:@":"];
            [classpathOption appendString:toolsJar];
        }
    } else {
        NSLog(@"Info.plist is corrupted, Absent ClassPath key.");
        exit(-1);
    }

    return classpathOption;
}

NSString *getJVMProperty(NSString *property) {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSDictionary *properties = [jvmInfo dictionaryForKey:@"Properties"];
    return properties != nil ? properties[property] : nil;
}

NSString *getSelector() {
    return getJVMProperty(@"idea.paths.selector");
}

NSString *getExecutable() {
    return getJVMProperty(@"idea.executable");
}

NSString *getPropertiesFilePath() {
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.properties"];
}

NSString *getPreferencesFolderPath() {
    return [NSString stringWithFormat:@"%@/Library/Application Support/%@/%@", NSHomeDirectory(), getJVMProperty(@"idea.vendor.name"), getSelector()];
}

NSString *getDefaultFilePath(NSString *fileName) {
    NSString *fullFileName = [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@"/Contents"];
    fullFileName = [fullFileName stringByAppendingString:fileName];
    NSLog(@"fullFileName is: %@", fullFileName);
    if ([[NSFileManager defaultManager] fileExistsAtPath:fullFileName]) {
        NSLog(@"fullFileName exists: %@", fullFileName);
    } else {
        fullFileName = [[[NSBundle mainBundle] bundlePath] stringByAppendingString:fileName];
        NSLog(@"fullFileName exists: %@", fullFileName);
    }
    return fullFileName;
}

NSArray *parseVMOptions() {
    NSString *vmOptionsFile = nil;
    NSMutableArray *vmOptions = nil, *userVmOptions = nil;

    NSString *variable = [[getExecutable() uppercaseString] stringByAppendingString:@"_VM_OPTIONS"];
    NSString *candidate = [[[NSProcessInfo processInfo] environment] objectForKey:variable];
    NSLog(@"parseVMOptions: %@ = %@", variable, candidate);
    if (candidate != nil) {
        // 1. $<IDE_NAME>_VM_OPTIONS
        vmOptions = [VMOptionsReader readFile:candidate];
        if (vmOptions != nil) {
            vmOptionsFile = candidate;
        }
    }
    else {
        // 2. <IDE_HOME>/bin/<bin_name>.vmoptions ...
        candidate = getDefaultFilePath([NSString stringWithFormat:@"/bin/%@.vmoptions", getExecutable()]);
        NSLog(@"parseVMOptions: %@", candidate);
        vmOptions = [VMOptionsReader readFile:candidate];
        if (vmOptions != nil) {
            vmOptionsFile = candidate;
        }
        // ... [+ <IDE_HOME>.vmoptions (Toolbox) || <config_directory>/<bin_name>.vmoptions]
        candidate = [NSString stringWithFormat:@"%@.vmoptions", [[NSBundle mainBundle] bundlePath]];
        NSLog(@"parseVMOptions: %@", candidate);
        userVmOptions = [VMOptionsReader readFile:candidate];
        if (userVmOptions != nil) {
            vmOptionsFile = candidate;
        } else {
            candidate = [NSString stringWithFormat:@"%@/%@.vmoptions", getPreferencesFolderPath(), getExecutable()];
            NSLog(@"parseVMOptions: %@", candidate);
            userVmOptions = [VMOptionsReader readFile:candidate];
            if (userVmOptions != nil) {
                vmOptionsFile = candidate;
            }
        }
    }

    NSLog(@"parseVMOptions: platform=%d user=%d file=%@",
          vmOptions == nil ? -1 : (int)[vmOptions count], userVmOptions == nil ? -1 : (int)[userVmOptions count], vmOptionsFile);

    if (userVmOptions != nil) {
        if (vmOptions == nil) {
            vmOptions = userVmOptions;
        } else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
            BOOL (^GC_lookup)(NSString *, NSUInteger, BOOL *) = ^BOOL(NSString *s, NSUInteger i, BOOL *stop) {
                return [s hasPrefix:@"-XX:+Use"] == YES && [s hasSuffix:@"GC"] == YES ? YES : NO;
            };
#pragma clang diagnostic pop
            if ([userVmOptions indexOfObjectPassingTest:GC_lookup] != NSNotFound) {
                NSUInteger gc = [vmOptions indexOfObjectPassingTest:GC_lookup];
                if (gc != NSNotFound) {
                    [vmOptions removeObjectAtIndex:gc];
                }
            }
            [vmOptions addObjectsFromArray:userVmOptions];
        }
    }

    if (vmOptionsFile != nil) {
        [vmOptions addObject:[NSString stringWithFormat:@"-Djb.vmOptionsFile=%@", vmOptionsFile]];
        return vmOptions;
    }
    else {
        NSAlert *alert = [[NSAlert alloc] init];
        [alert addButtonWithTitle:@"OK"];
        [alert setMessageText:@"Cannot find VM options file"];
        [alert setAlertStyle:NSAlertStyleWarning];
        [alert runModal];
        [alert release];
        return nil;
    }
}

NSString *getOverridePropertiesPath() {
    NSString *variable = [[getExecutable() uppercaseString] stringByAppendingString:@"_PROPERTIES"];
    return [[NSProcessInfo processInfo] environment][variable];
}

- (void)fillArgs:(NSMutableArray *)args_array fromOptions:(NSArray *)options fromProperties:(NSDictionary *)properties {
    if (options != nil) {
        for (id value in options) {
            [args_array addObject:value];
        }
    }
    if (properties != nil) {
        for (id key in properties) {
            [args_array addObject:[NSString stringWithFormat:@"-D%@=%@", key, properties[key]]];
        }
    }
}

- (JavaVMInitArgs)buildArgsFor:(NSBundle *)jvm {
    NSMutableString *classpathOption = [self buildClasspath:jvm];

    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSMutableArray *args_array = [NSMutableArray array];

    [args_array addObject:classpathOption];

    NSArray *vmOptions = parseVMOptions();
    if (vmOptions != nil) {
        [args_array addObjectsFromArray:vmOptions];
    }

    NSString *properties = getOverridePropertiesPath();
    if (properties != nil) {
        [args_array addObject:[NSString stringWithFormat:@"-Didea.properties.file=%@", properties]];
    }

    [self fillArgs:args_array fromOptions:[jvmInfo arrayForKey:@"Options"] fromProperties:[jvmInfo dictionaryForKey:@"Properties"]];

    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_6;
    args.ignoreUnrecognized = JNI_TRUE;

    NSUInteger nOptions = [args_array count];
    args.nOptions = (jint) nOptions;
    args.options = calloc(nOptions, sizeof(JavaVMOption));
    for (NSUInteger idx = 0; idx < nOptions; idx++) {
        id obj = args_array[idx];
        args.options[idx].optionString = strdup([[self expandMacros:[obj description]] UTF8String]);
    }
    return args;
}

- (const char *)mainClassName {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];

    NSString *mainClass = jvmInfo[@"MainClass"];
    if (mainClass == nil) {
        NSLog(@"Info.plist is corrupted, Absent MainClass key.");
        exit(-1);
    }

    char *answer = strdup([jvmInfo[@"MainClass"] UTF8String]);

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
    const char *cmd = getenv("_");
    if (cmd != NULL && strcmp(cmd, "/usr/bin/open") == 0) {
        const char *pwd = getenv("PWD");
        if (pwd != NULL && chdir(pwd) != 0) {
            NSLog(@"Cannot chdir() to %s", pwd);
        }
    }

    NSString *dir = [[NSFileManager defaultManager] currentDirectoryPath];
    NSLog(@"Current Directory: %@", dir);
}

BOOL validationJavaVersion(){
    vm = findMatchingVm();
    if (vm == nil) {
        return false;
    }
    return true;
}

- (void)launch {
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    if (vm == nil) {
        showWarning(@"No matching VM found.");
        NSLog(@"Cannot find matching VM, aborting");
        exit(-1);
    }

    NSError *error = nil;
    BOOL ok = [vm loadAndReturnError:&error];
    if (!ok) {
        NSLog(@"Cannot load JVM bundle: %@", error);
        int ret = -1;

#ifdef __arm64__
        requestRosetta(@(argv[0]));

        char **new_argv = calloc((size_t) argc + 3, sizeof(char *));
        new_argv[0] = "/usr/bin/arch";
        new_argv[1] = "-x86_64";
        memcpy(&new_argv[2], argv, argc * sizeof(char *));

        NSLog(@"Retrying as x86_64...");
        ret = execv("/usr/bin/arch", new_argv);
        perror("Could not launch as x86_64");
#endif

        exit(ret);
    }

    CFBundleRef cfvm = NSBundle2CFBundle(vm);

    fun_ptr_t_CreateJavaVM create_vm = (fun_ptr_t_CreateJavaVM) CFBundleGetFunctionPointerForName(cfvm, CFSTR("JNI_CreateJavaVM"));

    if (create_vm == NULL) {
        NSString *serverLibUrl = [vm.bundlePath stringByAppendingPathComponent:@"Contents/Libraries/libserver.dylib"];

        void *libHandle = dlopen(serverLibUrl.UTF8String, RTLD_NOW + RTLD_GLOBAL);
        if (libHandle) {
            create_vm = (fun_ptr_t_CreateJavaVM) dlsym(libHandle, "JNI_CreateJavaVM_Impl");
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
        NSString *serverLibUrl = [vm.bundlePath stringByAppendingPathComponent:@"Contents/Home/lib/server/libjvm.dylib"];

        void *libHandle = dlopen(serverLibUrl.UTF8String, RTLD_NOW + RTLD_GLOBAL);
        if (libHandle) {
            create_vm = (fun_ptr_t_CreateJavaVM) dlsym(libHandle, "JNI_CreateJavaVM");
        }
        
        if (create_vm != NULL) {
            create_vm_rc = create_vm(&jvm, &env, &args);
        }
        if (create_vm == NULL || create_vm_rc != JNI_OK) {
            NSLog(@"JNI_CreateJavaVM (%@) failed: %d", vm.bundlePath, create_vm_rc);
            exit(-1);
        }
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

    [pool release];
}
@end

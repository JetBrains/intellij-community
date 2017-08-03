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
@class NSAlert;

typedef jint (JNICALL *fun_ptr_t_CreateJavaVM)(JavaVM **pvm, void **env, void *args);
NSBundle *vm;
NSString *const JVMOptions = @"JVMOptions";
NSString *JVMVersion = NULL;
NSString* minRequiredJavaVersion = @"1.8";
NSString* osxVersion = @"10.10";
BOOL javaUpdateRequired = false;


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
- (id)valueForKey:(NSString *)key inDictionary:(NSString *)dictKey defaultObject:(NSString *)defaultValue;
@end

@implementation NSDictionary (TypedGetters)
- (NSDictionary *)dictionaryForKey:(id)key {
    id answer = [self objectForKey:key];
    if ([answer isKindOfClass:[NSDictionary class]]) {
        return answer;
    }
    return nil;
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

NSString* getOSXVersion(){
  NSString *versionString;
  NSDictionary * sv = [NSDictionary dictionaryWithContentsOfFile:@"/System/Library/CoreServices/SystemVersion.plist"];
  versionString = [sv objectForKey:@"ProductVersion"];
  //NSLog(@"OS X: %@", versionString);
  return versionString;
}

void showWarning(NSString* messageText){
   NSAlert* alert = [[NSAlert alloc] init];
   [alert addButtonWithTitle:@"OK"];
   NSString* message_description = [NSString stringWithFormat:@"Java 1.8 or later is required."];
   NSString* informativeText =[NSString stringWithFormat:@"%@",message_description];
   [alert setMessageText:messageText];
   [alert setInformativeText:informativeText ];
   [alert setAlertStyle:NSWarningAlertStyle];
   [alert runModal];
   [alert release];
}


BOOL appendBundle(NSString *path, NSMutableArray *sink) {
    if ([path hasSuffix:@"jdk"] || [path hasSuffix:@".jre"]) {
        NSBundle *bundle = [NSBundle bundleWithPath:path];
        if (bundle != nil) {
            [sink addObject:bundle];
            return true;
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
    NSMutableArray *jvmBundlePaths = [NSMutableArray array];

    // search java info in user's idea.properties
    NSString* ideaProperty = getPropertiesFilePath();
    if ([[NSFileManager defaultManager] fileExistsAtPath:ideaProperty]) {
        NSDictionary *inConfig =[PropertyFileReader readFile:ideaProperty];
        NSString* userJavaVersion =[inConfig objectForKey:@"JVMVersion"];
        if (userJavaVersion != nil && meetMinRequirements(userJavaVersion)) {
            JVMVersion = userJavaVersion;
        }
    }
    NSString *required = requiredJvmVersions();
    NSLog(@"allVms required %@", required);

    if (! jvmBundlePaths.count > 0 ) {
        NSBundle *bundle = [NSBundle mainBundle];
        NSString *appDir = [bundle.bundlePath stringByAppendingPathComponent:@"Contents"];

        if (!appendJvmBundlesAt([appDir stringByAppendingPathComponent:@"/jre"], jvmBundlePaths)) {
          appendBundle([appDir stringByAppendingPathComponent:@"/jdk"], jvmBundlePaths);
        }
        if ((jvmBundlePaths.count > 0) && (satisfies(jvmVersion(jvmBundlePaths[jvmBundlePaths.count-1]), required))) return jvmBundlePaths;

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

NSComparisonResult compareVMVersions(id vm1, id vm2, void *context) {
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
    NSString *explicit = [[[NSProcessInfo processInfo] environment] objectForKey:variable];
    if (explicit != nil) {
        NSLog(@"Value of %@: %@", variable, explicit);
        NSBundle *jdkBundle = getJDKBundle(explicit, @"environment variable");
        if (jdkBundle != nil) {
          return jdkBundle;
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

    if (requiredList != nil && requiredList != NULL) {
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
    NSString *classPath = [jvmInfo objectForKey:@"ClassPath"];
    if (classPath != nil && classPath != NULL) {
        [classpathOption appendString:[jvmInfo objectForKey:@"ClassPath"]];
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
    if (properties != nil) {
        return [properties objectForKey:property];
    }
    return nil;
}

NSString *getSelector() {
    return getJVMProperty(@"idea.paths.selector");
}

NSString *getExecutable() {
    return getJVMProperty(@"idea.executable");
}

NSString *getBundleName() {
    return [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleName"];
}

NSString *getPropertiesFilePath() {
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.properties"];
}


NSString *getPreferencesFolderPath() {
    return [NSString stringWithFormat:@"%@/Library/Preferences/%@", NSHomeDirectory(), getSelector()];
}

// NSString *getDefaultVMOptionsFilePath() {
//    return [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@fileName];

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

NSString *getToolboxVMOptionsPath() {
    return [NSString stringWithFormat:@"%@.vmoptions", [[NSBundle mainBundle] bundlePath]];
}

NSString *getApplicationVMOptionsPath() {
    return getDefaultFilePath([NSString stringWithFormat:@"/bin/%@.vmoptions", getExecutable()]);
}

NSString *getUserVMOptionsPath() {
    return [NSString stringWithFormat:@"%@/%@.vmoptions", getPreferencesFolderPath(), getExecutable()];
}

NSString *getOverrideVMOptionsPath() {
    NSString *variable = [[getExecutable() uppercaseString] stringByAppendingString:@"_VM_OPTIONS"];
    NSString *value = [[[NSProcessInfo processInfo] environment] objectForKey:variable];
    NSLog(@"Value of %@ is %@", variable, value);
    return value == nil ? @"" : value;
}

NSArray *parseVMOptions() {
    NSString *vmOptionsFile = getOverrideVMOptionsPath();
    if (! [[NSFileManager defaultManager] fileExistsAtPath:vmOptionsFile]) {
        vmOptionsFile = getToolboxVMOptionsPath();
    }
    if (! [[NSFileManager defaultManager] fileExistsAtPath:vmOptionsFile]) {
        vmOptionsFile = getUserVMOptionsPath();
    }
    if (! [[NSFileManager defaultManager] fileExistsAtPath:vmOptionsFile]) {
        vmOptionsFile = getApplicationVMOptionsPath();
    }

    NSMutableArray *options = [NSMutableArray array];

    NSLog(@"Processing VMOptions file at %@", vmOptionsFile);
    NSArray *contents = [VMOptionsReader readFile:vmOptionsFile];
    if (contents != nil) {
        NSLog(@"Done");
        [options addObjectsFromArray:contents];
        [options addObject:[NSString stringWithFormat:@"-Djb.vmOptionsFile=%@", vmOptionsFile]];
    } else {
        NSLog(@"No content found at %@", vmOptionsFile);
    }

    return options;
}

NSString *getOverridePropertiesPath() {
    NSString *variable = [[getExecutable() uppercaseString] stringByAppendingString:@"_PROPERTIES"];
    return [[[NSProcessInfo processInfo] environment] objectForKey:variable];
}

- (void)fillArgs:(NSMutableArray *)args_array fromProperties:(NSDictionary *)properties {
    if (properties != nil) {
        for (id key in properties) {
            [args_array addObject:[NSString stringWithFormat:@"-D%@=%@", key, [properties objectForKey:key]]];
        }
    }
}

- (JavaVMInitArgs)buildArgsFor:(NSBundle *)jvm {
    NSMutableString *classpathOption = [self buildClasspath:jvm];

    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSMutableArray *args_array = [NSMutableArray array];

    [args_array addObject:classpathOption];
    [args_array addObjectsFromArray:parseVMOptions()];
    [args_array addObject:[NSString stringWithFormat:@"-Xbootclasspath/a:../lib/boot.jar"]];

    NSString *properties = getOverridePropertiesPath();
    if (properties != nil) {
        [args_array addObject:[NSString stringWithFormat:@"-Didea.properties.file=%@", properties]];
    }

    [self fillArgs:args_array fromProperties:[jvmInfo dictionaryForKey:@"Properties"]];

    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_6;
    args.ignoreUnrecognized = JNI_TRUE;

    args.nOptions = (jint)[args_array count];
    args.options = calloc((size_t) args.nOptions, sizeof(JavaVMOption));
    for (NSUInteger idx = 0; idx < args.nOptions; idx++) {
        id obj = [args_array objectAtIndex:idx];
        args.options[idx].optionString = strdup([[self expandMacros:[obj description]] UTF8String]);
    }
    return args;
}

- (const char *)mainClassName {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];

    NSString *mainClass = [jvmInfo objectForKey:@"MainClass"];
    if (mainClass == nil || mainClass == NULL) {
        NSLog(@"Info.plist is corrupted, Absent MainClass key.");
        exit(-1);
    }

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
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSString *cwd = [jvmInfo objectForKey:@"WorkingDirectory"];
    if (cwd != nil && cwd != NULL) {
        cwd = [self expandMacros:cwd];
        if (chdir([cwd UTF8String]) != 0) {
            NSLog(@"Cannot chdir to working directory at %@", cwd);
        }
    } else {
        NSString *dir = [[NSFileManager defaultManager] currentDirectoryPath];
        NSLog(@"WorkingDirectory is absent in Info.plist. Current Directory: %@", dir);
    }
}

BOOL validationJavaVersion(){
    vm = findMatchingVm();
    if (vm == nil) {
        return false;
    }
    return true;
}

- (void)alert:(NSArray *)values {
    NSAlert *alert = [[[NSAlert alloc] init] autorelease];
    [alert setMessageText:[values objectAtIndex:0]];
    [alert setInformativeText:[values objectAtIndex:1]];

    if ([values count] > 2) {
        NSTextView *accessory = [[NSTextView alloc] initWithFrame:NSMakeRect(0, 0 , 300 , 15)];
        [accessory setFont:[NSFont systemFontOfSize:[NSFont smallSystemFontSize]]];
        NSMutableAttributedString *str = [[NSMutableAttributedString alloc] initWithString: [values objectAtIndex:2]];
        [str addAttribute: NSLinkAttributeName value: [values objectAtIndex:2] range: NSMakeRange(0, str.length)];
        [accessory insertText:str];
        [accessory setEditable:NO];
        [accessory setDrawsBackground:NO];
        [alert setAccessoryView:accessory];
    }

    [alert runModal];
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
        NSLog(@"JNI_CreateJavaVM (%@) failed: %ld", vm.bundlePath, create_vm_rc);
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

    [pool release];
}

@end
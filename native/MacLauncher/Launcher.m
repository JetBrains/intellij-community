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


//static NSString *const JVMOptions = @"JVMOptions";
NSString *JVMOptions;
NSString *WorkingDir;
io_connect_t _switcherConnect = IO_OBJECT_NULL;
io_service_t service = IO_OBJECT_NULL;
io_iterator_t iterator = IO_OBJECT_NULL;
    enum {
      kOpen,
      kClose,
      kSetMuxState,
      kGetMuxState,
      kSetExclusive,
      kDumpState,
      kUploadEDID,
      kGetAGCData,
      kGetAGCData_log1,
      kGetAGCData_log2,
      kNumberOfMethods};

  typedef enum {
    muxDisableFeature    = 0, // set only
    muxEnableFeature    = 1, // set only
    muxFeatureInfo        = 0, // get: returns a uint64_t with bits set according to FeatureInfos, 1=enabled
    muxFeatureInfo2        = 1, // get: same as MuxFeatureInfo
    muxForceSwitch        = 2, // set: force Graphics Switch regardless of switching mode
    // get: always returns 0xdeadbeef
    muxPowerGPU            = 3, // set: power down a gpu, pretty useless since you can't power down the igp and the dedicated gpu is powered down automatically
    // get: maybe returns powered on graphics cards, 0x8 = integrated, 0x88 = discrete (or probably both, since integrated never gets powered down?)
    muxGpuSelect        = 4, // set/get: Dynamic Switching on/off with [2] = 0/1 (the same as if you click the checkbox in systemsettings.app)
    // TODO: Test what happens on older mbps when switchpolicy = 0
    // Changes if you're able to switch in systemsettings.app without logout
    muxSwitchPolicy        = 5, // set: 0 = dynamic switching, 2 = no dynamic switching, exactly like older mbp switching, 3 = no dynamic stuck, others unsupported
    // get: possibly inverted?
    muxUnknown            = 6, // get: always 0xdeadbeef
    muxGraphicsCard        = 7, // get: returns active graphics card
    muxUnknown2            = 8, // get: sometimes 0xffffffff, TODO: figure out what that means
  } muxState;

  typedef enum {
      Policy,
      Auto_PowerDown_GPU,
      Dynamic_Switching,
      GPU_Powerpolling, // Inverted: Disable Feature enables it and vice versa
      Defer_Policy,
      Synchronous_Launch,
      Backlight_Control=8,
      Recovery_Timeouts,
      Power_Switch_Debounce,
      Logging=16,
      Display_Capture_Switch,
      No_GL_HDA_busy_idle_registration,
      muxFeaturesCount
    } muxFeature;

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


void appendBundle(NSString *path, NSMutableArray *sink) {
    NSLog(@"running appendBundle, path: %@", path);
    if ([path hasSuffix:@".jdk"] || [path hasSuffix:@".jre"]) {
        NSBundle *bundle = [NSBundle bundleWithPath:path];
        if (bundle != nil) {
            [sink addObject:bundle];
            NSLog(@"running appendBundle jdkbundle added");
        }
    }
}

void appendJvmBundlesAt(NSString *path, NSMutableArray *sink) {
    NSError *error = nil;
    NSArray *names = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:path error:&error];

    if (names != nil) {
        for (NSString *name in names) {
            appendBundle([path stringByAppendingPathComponent:name], sink);
        }
    }
}


NSString *getJavaKey(){
    NSArray *JavaKeysArray = [[NSArray alloc] initWithObjects:@"Java", @"JVMOptions", nil];
    NSLog(@"%@", JavaKeysArray);
 
    for (NSString *javaKey in JavaKeysArray) {
        NSLog(@"check javakey: %@", javaKey);

        NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:javaKey];
        
        if (jvmInfo != NULL) {
            NSLog(@"found javakey: %@", javaKey);
            return javaKey;
        }
    }
    NSLog(@"Info.plist is corrupted, Absent Java/JVMOptions key.");
    exit(-1);
}

NSArray *allVms() {
    NSMutableArray *jvmBundlePaths = [NSMutableArray array];
    NSString *explicit = [[[NSProcessInfo processInfo] environment] objectForKey:@"IDEA_JDK"];
    BOOL IDEA_JDK_verified = false;
    if (explicit != nil) {
      NSLog(@"value of IDEA_JDK: %@", explicit);
      NSBundle *jdkBundle = [NSBundle bundleWithPath:explicit];
      NSString *required = requiredJvmVersion();
      if (jdkBundle != nil && required != NULL) {
        NSLog(@"there is requirements");
        if (satisfies(jvmVersion(jdkBundle), required)) {
            appendBundle(explicit, jvmBundlePaths);
            debugLog(@"User VM:");
            debugLog([jdkBundle bundlePath]);
            IDEA_JDK_verified = true;
        }
      } else {
          NSLog(@"required == NULL");
      }
    }
    if (jvmBundlePaths == nil) {
        NSLog(@"no IDEA_JDK");
        NSBundle *bundle = [NSBundle mainBundle];
        NSString *appDir = [bundle.bundlePath stringByAppendingPathComponent:@"Contents"];
        NSLog(@"Running allVms. appDir define as: %@", appDir);

        appendJvmBundlesAt([appDir stringByAppendingPathComponent:@"/jre"], jvmBundlePaths);
        if (jvmBundlePaths.count > 0) return jvmBundlePaths;

        appendJvmBundlesAt([NSHomeDirectory() stringByAppendingPathComponent:@"Library/Java/JavaVirtualMachines"], jvmBundlePaths);
        appendJvmBundlesAt(@"/Library/Java/JavaVirtualMachines", jvmBundlePaths);
        appendJvmBundlesAt(@"/System/Library/Java/JavaVirtualMachines", jvmBundlePaths);
    }

    return jvmBundlePaths;
}

NSString *jvmVersion(NSBundle *bundle) {
    return [bundle.infoDictionary valueForKey:@"JVMVersion" inDictionary:@"JavaVM" defaultObject:@"0"];
}

NSString *requiredJvmVersion() {
    return [[NSBundle mainBundle].infoDictionary valueForKey:@"JVMVersion" inDictionary: JVMOptions defaultObject:@"1.7*"];
}

BOOL satisfies(NSString *vmVersion, NSString *requiredVersion) {
    NSLog(@"satisfies. vmVersion : %@", vmVersion);
    NSLog(@"satisfies. requiredVersion : %@", requiredVersion);

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

NSBundle *findMatchingVm() {
    NSArray *vmBundles = [allVms() sortedArrayUsingFunction:compareVMVersions context:NULL];

    if (isDebugEnabled()) {
        debugLog(@"Found Java Virtual Machines:");
        for (NSBundle *vm in vmBundles) {
            debugLog([vm bundlePath]);
        }
    }

    NSString *required = requiredJvmVersion();
    debugLog([NSString stringWithFormat:@"Required VM: %@", required]);

    if (required != NULL) {
      for (NSBundle *vm in vmBundles) {
          if (satisfies(jvmVersion(vm), required)) {
              debugLog(@"Chosen VM:");
              debugLog([vm bundlePath]);
              return vm;
          }
      }
    } else {
      NSLog(@"Info.plist is corrupted, Absent JVMVersion key.");
      exit(-1);
    }

    debugLog(@"No matching VM found");

    return nil;
}

CFBundleRef NSBundle2CFBundle(NSBundle *bundle) {
    NSLog(@"--- NSBundle2CFBundle");
    debugLog([bundle bundlePath]);
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
    [classpathOption appendString:[jvmInfo objectForKey:@"ClassPath"]];

    NSString *toolsJar = [[jvm bundlePath] stringByAppendingString:@"/Contents/Home/lib/tools.jar"];
    if ([[NSFileManager defaultManager] fileExistsAtPath:toolsJar]) {
        [classpathOption appendString:@":"];
        [classpathOption appendString:toolsJar];
    }
    return classpathOption;
}


NSString *getSelector() {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
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
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.properties"];
}

NSString *getDefaultPropertiesFilePath() {
    return [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@"/bin/idea.properties"];
}

// NSString *getDefaultVMOptionsFilePath() {
//    return [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@fileName];

NSString *getDefaultFilePath(NSString *fileName) {
    NSString *fullFileName = [[[NSBundle mainBundle] bundlePath] stringByAppendingString:@"/Contents"];
    fullFileName = [fullFileName stringByAppendingString:fileName];
    NSLog(@"fullFileName is: %@", fullFileName);
    if ([[NSFileManager defaultManager] fileExistsAtPath:fullFileName]) {
      NSLog(@"fullFileName exists: %@", fullFileName);
    } else{
      fullFileName = [[[NSBundle mainBundle] bundlePath] stringByAppendingString:fileName];
      NSLog(@"fullFileName exists: %@", fullFileName);
    }
    return fullFileName;
}

NSString *getVMOptionsFilePath() {
    return [getPreferencesFolderPath() stringByAppendingString:@"/idea.vmoptions"];
}

NSArray *parseVMOptions() {
    NSArray *inConfig=[VMOptionsReader readFile:getVMOptionsFilePath()];
    if (inConfig) return inConfig;

//    NSString *test = getDefaultVMOptionsFilePath();
//    NSLog(@"Original value of getDefaultVMOptionsFilePath is: %@", test);

//    return [VMOptionsReader readFile:getDefaultVMOptionsFilePath()];
    return [VMOptionsReader readFile:getDefaultFilePath(@"/bin/idea.vmoptions")];

}

NSDictionary *parseProperties() {
    NSDictionary *inConfig = [PropertyFileReader readFile:getPropertiesFilePath()];
    if (inConfig) return inConfig;
//    NSString *test = getDefaultPropertiesFilePath();
//    NSLog(@"Original value of getDefaultPropertiesFilePath is: %@", test);

//    return [PropertyFileReader readFile:getDefaultPropertiesFilePath()];
    return [PropertyFileReader readFile:getDefaultFilePath(@"/bin/idea.properties")];
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

    [args_array addObjectsFromArray:[[jvmInfo objectForKey:@"VMOptions"] componentsSeparatedByString:@" "]];
    [args_array addObjectsFromArray:parseVMOptions()];    

    [self fillArgs:args_array fromProperties:[jvmInfo dictionaryForKey:@"Properties"]];
    [self fillArgs:args_array fromProperties:parseProperties()];

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
    WorkingDir = [jvmInfo objectForKey:@"WorkingDirectory"];
    if (WorkingDir != NULL && WorkingDir != nil ) {
      NSLog(@"WorkingDir: %@", WorkingDir);
      char *answer = strdup([[jvmInfo objectForKey:@"MainClass"] UTF8String]);
    
      char *cur = answer;
      while (*cur) {
        if (*cur == '.') {
            *cur = '/';
        }
        cur++;
      }
    
      return answer;
    } else {
      NSLog(@"Info.plist is corrupted, Absent WorkingDir.");
      exit(-1);
    }

}

- (void)process_cwd {
    NSDictionary *jvmInfo = [[NSBundle mainBundle] objectForInfoDictionaryKey:JVMOptions];
    NSString *cwd = [jvmInfo objectForKey:@"WorkingDirectory"];
    if (cwd != nil) {
        cwd = [self expandMacros:cwd];
        if (chdir([cwd UTF8String]) != 0) {
            NSLog(@"Cannot chdir to working directory at %@", cwd);
        }
    }
}

BOOL setMuxState(io_connect_t connect, muxState state, uint64_t arg){
    //kern_return_t kernResult;
    uint64_t scalarI_64[3] = { 1 /* always? */, (uint64_t) state, arg };
    kern_return_t kernResult = IOConnectCallScalarMethod(connect,      // an io_connect_t returned from IOServiceOpen().
                                           kSetMuxState, // selector of the function to be called via the user client.
                                           scalarI_64,   // array of scalar (64-bit) input values.
                                           3,            // the number of scalar input values.
                                           NULL,         // array of scalar (64-bit) output values.
                                           0);           // pointer to the number of scalar output values.

    if (kernResult == KERN_SUCCESS)
        NSLog(@"setMuxState was successful.");
    else
        NSLog(@"setMuxState returned 0x%08x.", kernResult);

    return kernResult == KERN_SUCCESS;
}

BOOL getMuxState(io_connect_t connect, uint64_t input, uint64_t *output){
    kern_return_t kernResult;
    uint32_t outputCount = 1;
    uint64_t scalarI_64[2] = { 1 /* Always 1 (kMuxControl?) */, input /* Feature Info */ };

    kernResult = IOConnectCallScalarMethod(connect,       // an io_connect_t returned from IOServiceOpen().
                                           kGetMuxState,  // selector of the function to be called via the user client.
                                           scalarI_64,    // array of scalar (64-bit) input values.
                                           2,             // the number of scalar input values.
                                           output,        // array of scalar (64-bit) output values.
                                           &outputCount); // pointer to the number of scalar output values.

    if (kernResult == KERN_SUCCESS)
        NSLog(@"getMuxState was successful (count=%d, value=0x%08llx).", outputCount, *output);
    else
        NSLog(@"getMuxState returned 0x%08x.", kernResult);

    return kernResult == KERN_SUCCESS;
}


BOOL isUsingIntegratedGPU(){
    kern_return_t kernResult = IOServiceGetMatchingServices(kIOMasterPortDefault, IOServiceMatching("AppleGraphicsControl"), &iterator);
    if (kernResult != KERN_SUCCESS) {
        NSLog(@"IOServiceGetMatchingServices returned 0x%08x.", kernResult);
        return NO;
    }

    service = IOIteratorNext(iterator); // actually there is only 1 such service
    IOObjectRelease(iterator);
    if (service == IO_OBJECT_NULL) {
        NSLog(@"No matching drivers found.");
        return NO;
    }

    kernResult = IOServiceOpen(service, mach_task_self(), 0, &_switcherConnect);
    if (kernResult != KERN_SUCCESS) {
        NSLog(@"IOServiceOpen returned 0x%08x.", kernResult);
        return NO;
    }

    kernResult = IOConnectCallScalarMethod(_switcherConnect, kOpen, NULL, 0, NULL, NULL);
    if (kernResult != KERN_SUCCESS)
        NSLog(@"IOConnectCallScalarMethod returned 0x%08x.", kernResult);
    else
        NSLog(@"Driver connection opened.");


    uint64_t output;
    if (_switcherConnect == IO_OBJECT_NULL) return NO;
    // 7 - returns active graphics card
    getMuxState(_switcherConnect, 7, &output);
    return output != 0;
}


NSArray *getGPUNames (){
    NSMutableArray *_cachedGPUs = [NSMutableArray array];
    //#define kIOPCIDevice  "IOPCIDevice"

    // The IOPCIDevice class includes display adapters/GPUs.
    CFMutableDictionaryRef devices = IOServiceMatching("IOPCIDevice");
    io_iterator_t entryIterator;

    if (IOServiceGetMatchingServices(kIOMasterPortDefault, devices, &entryIterator) == kIOReturnSuccess) {
        io_registry_entry_t device;

        while ((device = IOIteratorNext(entryIterator))) {
            CFMutableDictionaryRef serviceDictionary;

            if (IORegistryEntryCreateCFProperties(device, &serviceDictionary, kCFAllocatorDefault, kNilOptions) != kIOReturnSuccess) {
                // Couldn't get the properties for this service, so clean up and continue.
                IOObjectRelease(device);
                continue;
            }

            const void *ioName = CFDictionaryGetValue(serviceDictionary, @"IOName");

            if (ioName) {
                // If we have an IOName, and its value is "display", then we've got a "model" key,
                // whose value is a CFDataRef that we can convert into a string.
                if (CFGetTypeID(ioName) == CFStringGetTypeID() && CFStringCompare(ioName, CFSTR("display"), kCFCompareCaseInsensitive) == kCFCompareEqualTo) {
                    const void *model = CFDictionaryGetValue(serviceDictionary, @"model");

                    NSString *gpuName = [[NSString alloc] initWithData:(__bridge NSData *)model
                                                              encoding:NSASCIIStringEncoding];

                    [_cachedGPUs addObject:gpuName];
                }
            }

            CFRelease(serviceDictionary);
        }
    }

    return _cachedGPUs;
}


NSString *integratedGPUNames() {
 NSString *_cachedIntegratedGPUName = nil;
 NSArray *gpus = getGPUNames();
 if (gpus != nil && gpus != NULL){
   NSLog(@"GPUs present: %@", gpus);
   for (NSString *gpu in gpus) {
   // Intel GPUs have always been the integrated ones in newer machines so far
     if ([gpu hasPrefix:@"Intel"]) {
      _cachedIntegratedGPUName = gpu;
      break;
     }
   }
 }
 return _cachedIntegratedGPUName;
}

- (void)launch {
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    JVMOptions = getJavaKey();
    NSBundle *vm = findMatchingVm();
    NSLog(@"--- launch");

//    if (! switcherOpen()) {
//        NSLog(@"Can't open connection to AppleGraphicsControl. This probably isn't a gfxCardStatus-compatible machine.");
//    } else {

        NSString *intCard = integratedGPUNames();
        if (intCard != nil) {
          NSLog(@"Integrated GPU name: %@", intCard);
          if (isUsingIntegratedGPU()){
            NSLog(@"Integrated GPU is used.");
            uint64_t output;
            getMuxState(_switcherConnect, muxGpuSelect, &output);
            if ( output != 0 ) {
              NSLog(@"Dynamic mode is using.");
              setMuxState(_switcherConnect, muxGpuSelect, 0);
              setMuxState(_switcherConnect, muxDisableFeature, 0);
              NSLog(@"Dynamic mode switch of.");
              getMuxState(_switcherConnect, muxGpuSelect, &output);
              if ( output != 0 ) {
                NSLog(@"Dynamic mode is using.");
                setMuxState(_switcherConnect, muxGpuSelect, 0);
                setMuxState(_switcherConnect, muxDisableFeature, 0);
              } else{
                NSLog(@"Integrated mode is using.");
              }
              getMuxState(_switcherConnect, muxSwitchPolicy, &output);
              if ( output != 0 ) {
                NSLog(@"OldStyleSwitchPolicy is Using");
                setMuxState(_switcherConnect, muxSwitchPolicy, 2);
              } else{
                NSLog(@"NewStyleSwitchPolicy is Using");
                setMuxState(_switcherConnect, muxSwitchPolicy, 0);
              }
            }
          } else{
            NSLog(@"Discrete GPU is used.");
            setMuxState(_switcherConnect, muxForceSwitch, 0);
            NSLog(@"Try to switch to integrate card.");
          }
        } else {
          NSLog(@"There is no Integrated GPU present.");
        }

    debugLog([vm bundlePath]);
    NSLog(@"vm == %@", vm);
    if (vm == nil) {
      NSLog(@"vm == nil.");
      NSString *old_launcher = [self expandMacros:@"$APP_PACKAGE/Contents/MacOS/idea_appLauncher"];
      execv([old_launcher fileSystemRepresentation], self->argv);
      NSLog(@"Cannot find matching VM, aborting");
      exit(-1);
    }

    NSError *error = nil;
    BOOL ok = [vm loadAndReturnError:&error];
    if (!ok) {
        NSLog(@"Cannot load JVM bundle: %@", error);
        exit(-1);
    }

    NSLog(@"--- launch");
    debugLog([vm bundlePath]);
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

    [pool release];
}


@end
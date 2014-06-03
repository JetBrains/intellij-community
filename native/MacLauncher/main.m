#import "Launcher.h"
#include <IOKit/ps/IOPowerSources.h>
#include <sys/cdefs.h>

#define kOldStyleSwitchPolicyValue (2) // log out before switching
#define kNewStyleSwitchPolicyValue (0) // dynamic switching
typedef jint (JNICALL *fun_ptr_t_CreateJavaVM)(JavaVM **pvm, void **env, void *args);

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

#define FOREVER ((CFTimeInterval) 1e20)

static void timer_empty(CFRunLoopTimerRef timer, void *info) {
}

static void update (void* context){
    NSLog(@" ****** Status of the Power adapter is changed. ");
}

static void parkRunLoop() {
    CFRunLoopTimerRef t = CFRunLoopTimerCreate(kCFAllocatorDefault, FOREVER, (CFTimeInterval)0.0, 0, 0, timer_empty, NULL);
    CFRunLoopAddTimer(CFRunLoopGetCurrent(), t, kCFRunLoopDefaultMode);
    NSLog(@"---- parkRunLoop CFRelease");
    CFRelease(t);
   
    void *context;
    CFRunLoopSourceRef sourceRef = IOPSNotificationCreateRunLoopSource(update,  context);
    if ( sourceRef != NULL){
        CFRunLoopAddSource(CFRunLoopGetCurrent(), sourceRef , kCFRunLoopDefaultMode);
        NSLog(@"---- notification about changes in power adapter is ON");
    }
    SInt32 result;
    NSLog(@"---- parkRunLoop result");
    do {
        result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, FOREVER, false);
    } while (result != kCFRunLoopRunFinished);
    NSLog(@"---- END parkRunLoop");
}

static void makeSameStackSize(NSThread *thread) {
    struct rlimit l;
    int err = getrlimit(RLIMIT_STACK, &l);
    if (err == ERR_SUCCESS && l.rlim_cur > 0) {
        thread.stackSize = (NSUInteger) l.rlim_cur;
    }
}

static void launchInNewThread(Launcher *launcher) {
    NSThread *thread = [[[NSThread alloc] initWithTarget:launcher selector:@selector(launch) object:nil] autorelease];
    makeSameStackSize(thread);
    [thread start];
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

    if (kernResult != KERN_SUCCESS)
//        NSLog(@"setMuxState was successful.");
//    else
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

    if (kernResult != KERN_SUCCESS)
//        NSLog(@"getMuxState was successful (count=%d, value=0x%08llx).", outputCount, *output);
//    else
        NSLog(@"getMuxState returned 0x%08x.", kernResult);

    return kernResult == KERN_SUCCESS;
}


BOOL isUsingIntegratedGPU(){
    uint64_t output;
    if (_switcherConnect == IO_OBJECT_NULL) return NO;
    // 7 - returns active graphics card
    getMuxState(_switcherConnect, 7, &output);
    return output != 0;
}

BOOL switcherOpen(){
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

    return kernResult == KERN_SUCCESS;
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

static void setGUP(){
    if (! switcherOpen()) {
        NSLog(@"Can't open connection to AppleGraphicsControl. There is no a possibility to switch GPU.");
    } else {
        // check if the macbook is working on battery
        CFDictionaryRef powerAdapter = IOPSCopyExternalPowerAdapterDetails();
        if (powerAdapter == NULL) {
           NSLog(@"Battery is used. ");
           NSString *intCard = integratedGPUNames();
           if (intCard != nil) {
                NSLog(@"Integrated GPU name: %@", intCard);
                if (isUsingIntegratedGPU()){
                    NSLog(@"Integrated GPU is used.");

                    // Disable dynamic switching
                    setMuxState(_switcherConnect, muxGpuSelect, 0);
                    NSLog(@"Dynamic mode is switching OFF.");
                    setMuxState(_switcherConnect, muxDisableFeature, 1<<Policy);
                    setMuxState(_switcherConnect, muxSwitchPolicy, kOldStyleSwitchPolicyValue);

                    NSLog(@"After disable dynamic mode 5 sec wait");
                    sleep(1);

                    if (isUsingIntegratedGPU()){
                        NSLog(@"Integrated GPU is used.");
                    } else{
                        NSLog(@"Discrete GPU is used.");
                        setMuxState(_switcherConnect, muxForceSwitch, 0);
                        NSLog(@"Try to switch to integrate card.");

                        NSLog(@"Wait for 40 sec");
                        sleep(40);
                        NSLog(@"Dynamic mode is switching ON. 30 sec sleep");
                        //setMuxState(_switcherConnect, muxEnableFeature, 1<<Policy);
                        //setMuxState(_switcherConnect, muxSwitchPolicy, kNewStyleSwitchPolicyValue);
                        //setMuxState(_switcherConnect, muxGpuSelect, 1);
                        sleep(30);
                        NSLog(@"**** The GPU and mode of switching are restored. ****");
                    }
                } else{
                    NSLog(@"Discrete GPU is used.");
                    //            setMuxState(_switcherConnect, muxForceSwitch, 0);
                    //            NSLog(@"Try to switch to integrate card.");
                }
            } else {
                NSLog(@"There is no Integrated GPU present.");
            }
        } else {
          IOObjectRelease(powerAdapter);
          NSLog(@"The power cable is switch ON. Battery is NOT used. ");
        }
    }
}



int main(int argc, char *argv[]) {
    NSLog(@"---- Begin main");
   
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    NSLog(@"---- power notification");
    ßlaunchInNewThread([[[Launcher alloc] initWithArgc:argc argv:argv] autorelease]);

    //setGUP();
    NSLog(@"---- parkRunLoop");
    parkRunLoop();
    
    NSLog(@"---- End main");

    [pool release];
    return 0;
}

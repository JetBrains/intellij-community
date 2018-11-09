#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

#import "JTypes.h"

void nstrace(NSString *format, ...);
void nserror(NSString *format, ...);

// Next functions create autorelease image/strings

NSImage * createImgFrom4ByteRGBA(const unsigned char *bytes, int w, int h);
NSImage * createImg(ScrubberItemData *jdata);
NSString * createString(ScrubberItemData *jdata);
NSString * createStringFromUTF8(const char *utf8);

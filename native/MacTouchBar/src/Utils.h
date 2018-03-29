#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

#import "JTypes.h"

void nstrace(NSString *format, ...);
void nserror(NSString *format, ...);
NSImage * createImgFrom4ByteRGBA(const unsigned char *bytes, int w, int h); // Creates autorelease image

NSImage * getImg(ScrubberItemData * jdata);
NSString * getText(ScrubberItemData * jdata);
NSString * getString(const char * utf8);

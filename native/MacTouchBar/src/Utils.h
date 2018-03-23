#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

void nstrace(NSString *format, ...);
void nserror(NSString *format, ...);
NSImage * createImgFrom4ByteRGBA(const unsigned char *bytes, int w, int h); // Creates autorelease image

#import "ItemDesc.h"

static NSImage * createImgFrom4ByteRGBA(const unsigned char *bytes, int w, int h) {
    const int rowBytes = w*4;
    NSBitmapImageRep *rep =
            [[NSBitmapImageRep alloc]
                    initWithBitmapDataPlanes: nil  // allocate the pixel buffer inside
                                  pixelsWide: w
                                  pixelsHigh: h
                               bitsPerSample: 8
                             samplesPerPixel: 4
                                    hasAlpha: YES
                                    isPlanar: NO
                              colorSpaceName: NSDeviceRGBColorSpace
                                bitmapFormat: NSAlphaNonpremultipliedBitmapFormat
                                 bytesPerRow: rowBytes
                                bitsPerPixel: 0];   // this must agree with bitsPerSample and samplesPerPixel, If you specify 0 for this parameter, the object interprets the number of bits per pixel using the values in the bps and spp parameters

    [rep autorelease];

    unsigned char* pix = [rep bitmapData];
    memcpy(pix, bytes, h*rowBytes);

    NSImage* nsimg = [[[NSImage alloc] initWithSize:NSMakeSize(w, h)] autorelease];
    [nsimg addRepresentation:rep];
    return nsimg;
}

@implementation ItemDesc
@synthesize uid = _itemId;
@end

@implementation SpacingDesc
- (id)init:(NSString *)type {
    self = [super init];
    if (self) {
        if ([type isEqualToString:@"small"]) {
            self.uid = NSTouchBarItemIdentifierFixedSpaceSmall;
        } else if ([type isEqualToString:@"large"]) {
            self.uid = NSTouchBarItemIdentifierFixedSpaceLarge;
        } else if ([type isEqualToString:@"flexible"]) {
            self.uid = NSTouchBarItemIdentifierFlexibleSpace;
        }
    }
    return self;
}
@end

@implementation ButtonDesc
- (id)init:(callback)act {
    self = [super init];
    if (self)
        _action = act;
    return self;
}
@synthesize action = _action;
@end

@implementation ButtonImgDesc
- (id)init:(char*)raster width:(int)w height:(int)h act:(callback)act {
    self = [super init:act];
    if (self)
        self.img = createImgFrom4ByteRGBA((unsigned char *)raster, w, h);
    return self;
}
@synthesize img = _img;
@end

@implementation ButtonTextDesc
- (id)init:(NSString*)text act:(callback)act {
    self = [super init:act];
    if (self)
        self.text = text;
    return self;
}
@synthesize text = _text;
@end

@implementation PopoverDesc
- (id)init:(NSString *)text img:(char *)raster imgW:(int)w imgH:(int)h popoverWidth:(int)popW {
    self = [super init];
    if (self) {
        self.img = createImgFrom4ByteRGBA((unsigned char *)raster, w, h);
        self.text = text;
        self.width = popW;
    }
    return self;
}
@synthesize img = _img, text = _text, expandBar = _expandBar, tapHoldBar = _tapAndHoldBar;
@end

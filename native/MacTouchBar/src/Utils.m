#import "Utils.h"

//#define LOGGING_ERRORS_ENABLED
//#define LOGGING_TRACE_ENABLED

void nserror(NSString *format, ...) {
#ifdef LOGGING_ERRORS_ENABLED
    va_list argList;
    va_start(argList, format);
    NSString* formattedMessage = [[NSString alloc] initWithFormat: format arguments: argList];
    va_end(argList);
    NSLog(@"ERROR: %@", formattedMessage);
    [formattedMessage release];
#endif // LOGGING_ERRORS_ENABLED
}

void nstrace(NSString *format, ...) {
#ifdef LOGGING_TRACE_ENABLED
    va_list argList;
    va_start(argList, format);
    NSString* formattedMessage = [[NSString alloc] initWithFormat: format arguments: argList];
    va_end(argList);
    NSLog(@"TRACE: %@", formattedMessage);
    [formattedMessage release];
#endif // LOGGING_TRACE_ENABLED
}

NSImage * createImgFrom4ByteRGBA(const unsigned char *bytes, int w, int h) {
    if (bytes == NULL || w <= 0 || h <= 0)
        return nil;

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

    NSSize sizeInPoints = NSMakeSize(w/2, h/2); // NOTE: 'retina' display has 2 pix in 1 point
    NSImage* nsimg = [[[NSImage alloc] initWithSize:sizeInPoints] autorelease];
    [nsimg addRepresentation:rep];
    return nsimg;
}

NSImage * createImg(ScrubberItemData *jdata) {
    if (jdata == NULL)
        return nil;
    return createImgFrom4ByteRGBA((const unsigned char *)jdata->raster4ByteRGBA, jdata->rasterW, jdata->rasterH);
}

NSString * createString(ScrubberItemData *jdata) {
    if (jdata == NULL || jdata->text == NULL)
        return nil;
    return [NSString stringWithUTF8String:jdata->text];
}

NSString * createStringFromUTF8(const char *utf8) {
    if (utf8 == NULL)
        return nil;
    return [NSString stringWithUTF8String:utf8];
}

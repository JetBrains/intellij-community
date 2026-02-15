package org.jetbrains.jewel.window.utils.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer

@Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
    "Unused",
    "FunctionName",
    "ConstPropertyName",
) // Borrowed code
internal interface FoundationLibrary : Library {
    fun NSLog(pString: Pointer?, thing: Any?)

    fun NSFullUserName(): ID?

    fun objc_allocateClassPair(supercls: ID?, name: String?, extraBytes: Int): ID?

    fun objc_registerClassPair(cls: ID?)

    fun CFStringCreateWithBytes(
        allocator: Pointer?,
        bytes: ByteArray?,
        byteCount: Int,
        encoding: Int,
        isExternalRepresentation: Byte,
    ): ID?

    fun CFStringGetCString(theString: ID?, buffer: ByteArray?, bufferSize: Int, encoding: Int): Byte

    fun CFStringGetLength(theString: ID?): Int

    fun CFStringConvertNSStringEncodingToEncoding(nsEncoding: Long): Long

    fun CFStringConvertEncodingToIANACharSetName(cfEncoding: Long): ID?

    fun CFStringConvertIANACharSetNameToEncoding(encodingName: ID?): Long

    fun CFStringConvertEncodingToNSStringEncoding(cfEncoding: Long): Long

    fun CFRetain(cfTypeRef: ID?)

    fun CFRelease(cfTypeRef: ID?)

    fun CFGetRetainCount(cfTypeRef: Pointer?): Int

    fun objc_getClass(className: String?): ID?

    fun objc_getProtocol(name: String?): ID?

    fun class_createInstance(pClass: ID?, extraBytes: Int): ID?

    fun sel_registerName(selectorName: String?): Pointer?

    fun class_replaceMethod(cls: ID?, selName: Pointer?, impl: Callback?, types: String?): ID?

    fun objc_getMetaClass(name: String?): ID?

    /**
     * Note: Vararg version. Should only be used only for selectors with a single fixed argument followed by varargs.
     */
    fun objc_msgSend(receiver: ID?, selector: Pointer?, firstArg: Any?, vararg args: Any?): ID?

    fun class_respondsToSelector(cls: ID?, selName: Pointer?): Boolean

    fun class_addMethod(cls: ID?, selName: Pointer?, imp: Callback?, types: String?): Boolean

    fun class_addMethod(cls: ID?, selName: Pointer?, imp: ID?, types: String?): Boolean

    fun class_addProtocol(aClass: ID?, protocol: ID?): Boolean

    fun class_isMetaClass(cls: ID?): Boolean

    fun NSStringFromSelector(selector: Pointer?): ID?

    fun NSStringFromClass(aClass: ID?): ID?

    fun objc_getClass(clazz: Pointer?): Pointer?

    companion object {
        const val kCFStringEncodingMacRoman = 0
        const val kCFStringEncodingWindowsLatin1 = 0x0500
        const val kCFStringEncodingISOLatin1 = 0x0201
        const val kCFStringEncodingNextStepLatin = 0x0B01
        const val kCFStringEncodingASCII = 0x0600
        const val kCFStringEncodingUnicode = 0x0100
        const val kCFStringEncodingUTF8 = 0x08000100
        const val kCFStringEncodingNonLossyASCII = 0x0BFF
        const val kCFStringEncodingUTF16 = 0x0100
        const val kCFStringEncodingUTF16BE = 0x10000100
        const val kCFStringEncodingUTF16LE = 0x14000100
        const val kCFStringEncodingUTF32 = 0x0c000100
        const val kCFStringEncodingUTF32BE = 0x18000100
        const val kCFStringEncodingUTF32LE = 0x1c000100

        // https://developer.apple.com/library/mac/documentation/Carbon/Reference/CGWindow_Reference/Constants/Constants.html#//apple_ref/doc/constant_group/Window_List_Option_Constants
        const val kCGWindowListOptionAll = 0
        const val kCGWindowListOptionOnScreenOnly = 1
        const val kCGWindowListOptionOnScreenAboveWindow = 2
        const val kCGWindowListOptionOnScreenBelowWindow = 4
        const val kCGWindowListOptionIncludingWindow = 8
        const val kCGWindowListExcludeDesktopElements = 16

        // https://developer.apple.com/library/mac/documentation/Carbon/Reference/CGWindow_Reference/Constants/Constants.html#//apple_ref/doc/constant_group/Window_Image_Types
        const val kCGWindowImageDefault = 0
        const val kCGWindowImageBoundsIgnoreFraming = 1
        const val kCGWindowImageShouldBeOpaque = 2
        const val kCGWindowImageOnlyShadows = 4
        const val kCGWindowImageBestResolution = 8
        const val kCGWindowImageNominalResolution = 16

        // see enum NSBitmapImageFileType
        const val NSBitmapImageFileTypeTIFF = 0
        const val NSBitmapImageFileTypeBMP = 1
        const val NSBitmapImageFileTypeGIF = 2
        const val NSBitmapImageFileTypeJPEG = 3
        const val NSBitmapImageFileTypePNG = 4
        const val NSBitmapImageFileTypeJPEG2000 = 5
    }
}

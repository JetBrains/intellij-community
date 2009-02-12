package com.intellij.ui.growl;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.Native;

import java.io.UnsupportedEncodingException;

/**
 * @author spleaner
 */
public class Foundation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.growl.Foundation");

  private static final FoundationLibrary myFoundationLibrary;

  static {
    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    myFoundationLibrary = (FoundationLibrary) Native.loadLibrary("Foundation", FoundationLibrary.class);
  }

  private Foundation() {
  }

  /**
   * Get the ID of the NSClass with className
   */
  public static ID getClass(String className) {
    LOG.debug(String.format("calling objc_getClass(%s)", className));
    return myFoundationLibrary.objc_getClass(className);
  }

  public static Selector createSelector(String s) {
    return myFoundationLibrary.sel_registerName(s).initName(s);
  }

  public static ID invoke(final ID id, final Selector selector, Object... args) {
    return myFoundationLibrary.objc_msgSend(id, selector, args);
  }

  /**
   * Return a CFString as an ID, toll-free bridged to NSString.
   *
   * Note that the returned string must be freed with {@link #cfRelease(ID)}.
   */
  public static ID cfString(String s) {
      // Use a byte[] rather than letting jna do the String -> char* marshalling itself.
      // Turns out about 10% quicker for long strings.
      try {
          byte[] utf16Bytes = s.getBytes("UTF-16LE");
          return myFoundationLibrary.CFStringCreateWithBytes(null, utf16Bytes,
                  utf16Bytes.length, 0x14000100, (byte) 0); /* kTextEncodingUnicodeDefault + kUnicodeUTF16LEFormat */
      } catch (UnsupportedEncodingException x) {
          throw new RuntimeException(x);
      }
  }

  public static void cfRetain(final ID id) {
    myFoundationLibrary.CFRetain(id);
  }

  public static void cfRelease(final ID id) {
    myFoundationLibrary.CFRelease(id);
  }
}

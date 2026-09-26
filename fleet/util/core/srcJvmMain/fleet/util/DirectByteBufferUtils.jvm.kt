@file : Suppress ("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package fleet.util;

import java.nio.ByteBuffer


fun getAddress(buffer: ByteBuffer): Long? {
  return if (buffer is sun.nio.ch.DirectBuffer) {
    buffer.address()
  }
  else {
    null
  }
}
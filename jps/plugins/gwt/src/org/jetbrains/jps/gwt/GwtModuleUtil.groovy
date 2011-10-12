package org.jetbrains.jps.gwt

import org.xml.sax.SAXParseException;

/**
 * @author nik
 */
class GwtModuleUtil {
  public static boolean hasEntryPoints(File child) {
    try {
      def root = new XmlParser(false, false).parse(child)
      return !root."entry-point".isEmpty()
    }
    catch (IOException e) {
      return true;
    }
    catch (SAXParseException e) {
      return true;
    }
  }
}

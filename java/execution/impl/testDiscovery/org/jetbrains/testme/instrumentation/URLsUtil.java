/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.testme.instrumentation;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
public class URLsUtil {
  public static final String FILE = "file";
  public static final String PROTOCOL_DELIMITER = ":";
  public static final String JAR_DELIMITER = "!";

  public static boolean startsWithChar(CharSequence s, char prefix) {
return s != null && s.length() != 0 && s.charAt(0) == prefix;
}

  public static String extractRoot(URL resourceURL, String resourcePath) {
   if (!(startsWithChar(resourcePath, '/') || startsWithChar(resourcePath, '\\'))) {
     //noinspection HardCodedStringLiteral
     System.err.println("precondition failed: "+resourcePath);
     return null;
   }
   String protocol = resourceURL.getProtocol();
   String resultPath = null;

   if (FILE.equals(protocol)) {
     String path = resourceURL.getFile();
     final String testPath = path.replace('\\', '/');
     final String testResourcePath = resourcePath.replace('\\', '/');
     if (endsWithIgnoreCase(testPath, testResourcePath)) {
       resultPath = path.substring(0, path.length() - resourcePath.length());
     }
   }
   else if ("jar".equals(protocol)) {
     String fullPath = resourceURL.getFile();
     int delimiter = fullPath.indexOf(JAR_DELIMITER);
     if (delimiter >= 0) {
       String archivePath = fullPath.substring(0, delimiter);
       if (startsWithConcatenationOf(archivePath, FILE, PROTOCOL_DELIMITER)) {
         resultPath = archivePath.substring(FILE.length() + PROTOCOL_DELIMITER.length());
       }
     }
   }
   if (resultPath == null) {
     //noinspection HardCodedStringLiteral
     System.err.println("cannot extract: "+resultPath + " from "+resourceURL);
     return null;
   }

    if (resourcePath.endsWith(File.separator)) {
      resultPath = resultPath.substring(0, resultPath.lastIndexOf(File.separator));
    }
    resultPath = unescapePercentSequences(resultPath);
   return resultPath;
 }

  public static boolean startsWithConcatenationOf(String testee, String firstPrefix, String secondPrefix) {
   int l1 = firstPrefix.length();
   int l2 = secondPrefix.length();
   if (testee.length() < l1 + l2) return false;
   return testee.startsWith(firstPrefix) && testee.regionMatches(l1, secondPrefix, 0, l2);
 }

  public static boolean endsWithIgnoreCase(String str, String suffix) {
   final int stringLength = str.length();
   final int suffixLength = suffix.length();
   return stringLength >= suffixLength && str.regionMatches(true, stringLength - suffixLength, suffix, 0, suffixLength);
 }

  public static String unescapePercentSequences(String s) {
    if (s.indexOf('%') == -1) {
      return s;
    }

    StringBuilder decoded = new StringBuilder();
    final int len = s.length();
    int i = 0;
    while (i < len) {
      char c = s.charAt(i);
      if (c == '%') {
        List bytes = new ArrayList();
        while (i + 2 < len && s.charAt(i) == '%') {
          final int d1 = decode(s.charAt(i + 1));
          final int d2 = decode(s.charAt(i + 2));
          if (d1 != -1 && d2 != -1) {
            bytes.add(new Integer(((d1 & 0xf) << 4 | d2 & 0xf)));
            i += 3;
          } else {
            break;
          }
        }
        if (!bytes.isEmpty()) {
          final byte[] bytesArray = new byte[bytes.size()];
          for (int j = 0; j < bytes.size(); j++) {
            bytesArray[j] = (byte) ((Integer) bytes.get(j)).intValue();
          }
          try {
            decoded.append(new String(bytesArray, "UTF-8"));
            continue;
          }
          catch (UnsupportedEncodingException ignored) {
          }
        }
      }

      decoded.append(c);
      i++;
    }
    return decoded.toString();
  }

  private static int decode(char c) {
    if ((c >= '0') && (c <= '9'))
      return c - '0';
    if ((c >= 'a') && (c <= 'f'))
      return c - 'a' + 10;
    if ((c >= 'A') && (c <= 'F'))
      return c - 'A' + 10;
    return -1;
  }
}
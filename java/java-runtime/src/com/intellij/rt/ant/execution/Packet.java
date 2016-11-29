/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.rt.ant.execution;

public class Packet extends PacketWriter {
  public static final char ourSpecialSymbol = '$';
  public static final char[] ourSymbolsToEncode = new char[] {'\n', '\r', SegmentedStream.SPECIAL_SYMBOL};
  public static final int CODE_LENGTH = 2;

  public static String encode(String packet) {
    StringBuffer buffer = new StringBuffer(packet.length());
    for (int i = 0; i < packet.length(); i++) {
      char chr = packet.charAt(i);
      if (chr == ourSpecialSymbol) {
        buffer.append(chr);
        buffer.append(chr);
        continue;
      }
      boolean appendChar = true;
      for (int j = 0; j < ourSymbolsToEncode.length; j++) {
        if (ourSymbolsToEncode[j] == chr) {
          buffer.append(ourSpecialSymbol);
          final String code = String.valueOf((int)chr);
          for (int count = CODE_LENGTH - code.length(); count > 0; count--) {
            buffer.append("0");
          }
          buffer.append(code);
          appendChar = false;
          break;
        }
      }
      if (appendChar) {
        buffer.append(chr);
      }
    }
    return buffer.toString();
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide;

import java.awt.event.KeyEvent;
import java.util.HashMap;

/**
 * @author Denis Fokin
 */
class CharToVKeyMap {

  private CharToVKeyMap() {}

  private static HashMap<Character, Integer> charToVKeyMap =
    new HashMap<>();

  public static Integer get (Character ch) {
    return charToVKeyMap.containsKey(ch) ? charToVKeyMap.get(ch) : KeyEvent.VK_UNDEFINED;
  }

  static {
    charToVKeyMap.put(',', KeyEvent.VK_COMMA);
    charToVKeyMap.put('-',KeyEvent.VK_MINUS);
    charToVKeyMap.put('.',KeyEvent.VK_PERIOD);
    charToVKeyMap.put('/',KeyEvent.VK_SLASH);
    charToVKeyMap.put(';',KeyEvent.VK_SEMICOLON);
    charToVKeyMap.put('=',KeyEvent.VK_EQUALS);
    charToVKeyMap.put('[',KeyEvent.VK_OPEN_BRACKET);
    charToVKeyMap.put('\\',KeyEvent.VK_BACK_SLASH);
    charToVKeyMap.put(']',KeyEvent.VK_CLOSE_BRACKET);
    charToVKeyMap.put('`',KeyEvent.VK_BACK_QUOTE);
    charToVKeyMap.put('\'',KeyEvent.VK_QUOTE);
    charToVKeyMap.put('&',KeyEvent.VK_AMPERSAND);
    charToVKeyMap.put('*',KeyEvent.VK_ASTERISK);
    charToVKeyMap.put('"',KeyEvent.VK_QUOTEDBL);
    charToVKeyMap.put('<',KeyEvent.VK_LESS);
    charToVKeyMap.put('>',KeyEvent.VK_GREATER);
    charToVKeyMap.put('{',KeyEvent.VK_BRACELEFT);
    charToVKeyMap.put('}',KeyEvent.VK_BRACERIGHT);
    charToVKeyMap.put('@',KeyEvent.VK_AT);
    charToVKeyMap.put(':',KeyEvent.VK_COLON);
    charToVKeyMap.put('$',KeyEvent.VK_DOLLAR);
    charToVKeyMap.put('€',KeyEvent.VK_EURO_SIGN);
    charToVKeyMap.put('!',KeyEvent.VK_EXCLAMATION_MARK);
    charToVKeyMap.put('¡',KeyEvent.VK_INVERTED_EXCLAMATION_MARK);
    charToVKeyMap.put('(',KeyEvent.VK_LEFT_PARENTHESIS);
    charToVKeyMap.put('#',KeyEvent.VK_NUMBER_SIGN);
    charToVKeyMap.put('+',KeyEvent.VK_PLUS);
    charToVKeyMap.put(')',KeyEvent.VK_RIGHT_PARENTHESIS);
    charToVKeyMap.put('_',KeyEvent.VK_UNDERSCORE);

    // All these keys should be similar

    //charToVKeyMap.put('',KeyEvent.VK_ENTER);
    //charToVKeyMap.put('',KeyEvent.VK_BACK_SPACE);
    //charToVKeyMap.put('',KeyEvent.VK_TAB);
    //charToVKeyMap.put('',KeyEvent.VK_CANCEL);
    //charToVKeyMap.put('',KeyEvent.VK_CLEAR);
    //charToVKeyMap.put('',KeyEvent.VK_SHIFT);
    //charToVKeyMap.put('',KeyEvent.VK_CONTROL);
    //charToVKeyMap.put('',KeyEvent.VK_ALT);
    //charToVKeyMap.put('',KeyEvent.VK_PAUSE);
    //charToVKeyMap.put('',KeyEvent.VK_CAPS_LOCK);
    //charToVKeyMap.put('',KeyEvent.VK_ESCAPE);
    //charToVKeyMap.put('',KeyEvent.VK_SPACE);
    //charToVKeyMap.put('',KeyEvent.VK_PAGE_UP);
    //charToVKeyMap.put('',KeyEvent.VK_PAGE_DOWN);
    //charToVKeyMap.put('',KeyEvent.VK_END);
    //charToVKeyMap.put('',KeyEvent.VK_HOME);
    //charToVKeyMap.put('',KeyEvent.VK_LEFT);
    //charToVKeyMap.put('',KeyEvent.VK_UP);
    //charToVKeyMap.put('',KeyEvent.VK_RIGHT);
    //charToVKeyMap.put('',KeyEvent.VK_DOWN);
    //charToVKeyMap.put('0',KeyEvent.VK_0);
    //charToVKeyMap.put('1',KeyEvent.VK_1);
    //charToVKeyMap.put('2',KeyEvent.VK_2);
    //charToVKeyMap.put('3',KeyEvent.VK_3);
    //charToVKeyMap.put('4',KeyEvent.VK_4);
    //charToVKeyMap.put('5',KeyEvent.VK_5);
    //charToVKeyMap.put('6',KeyEvent.VK_6);
    //charToVKeyMap.put('7',KeyEvent.VK_7);
    //charToVKeyMap.put('8',KeyEvent.VK_8);
    //charToVKeyMap.put('9',KeyEvent.VK_9);

    //charToVKeyMap.put('a',KeyEvent.VK_A);
    //charToVKeyMap.put('b',KeyEvent.VK_B);
    //charToVKeyMap.put('c',KeyEvent.VK_C);
    //charToVKeyMap.put('d',KeyEvent.VK_D);
    //charToVKeyMap.put('e',KeyEvent.VK_E);
    //charToVKeyMap.put('f',KeyEvent.VK_F);
    //charToVKeyMap.put('g',KeyEvent.VK_G);
    //charToVKeyMap.put('h',KeyEvent.VK_H);
    //charToVKeyMap.put('i',KeyEvent.VK_I);
    //charToVKeyMap.put('j',KeyEvent.VK_J);
    //charToVKeyMap.put('k',KeyEvent.VK_K);
    //charToVKeyMap.put('l',KeyEvent.VK_L);
    //charToVKeyMap.put('m',KeyEvent.VK_M);
    //charToVKeyMap.put('n',KeyEvent.VK_N);
    //charToVKeyMap.put('o',KeyEvent.VK_O);
    //charToVKeyMap.put('p',KeyEvent.VK_P);
    //charToVKeyMap.put('q',KeyEvent.VK_Q);
    //charToVKeyMap.put('r',KeyEvent.VK_R);
    //charToVKeyMap.put('s',KeyEvent.VK_S);
    //charToVKeyMap.put('t',KeyEvent.VK_T);
    //charToVKeyMap.put('u',KeyEvent.VK_U);
    //charToVKeyMap.put('v',KeyEvent.VK_V);
    //charToVKeyMap.put('w',KeyEvent.VK_W);
    //charToVKeyMap.put('x',KeyEvent.VK_X);
    //charToVKeyMap.put('y',KeyEvent.VK_Y);
    //charToVKeyMap.put('z',KeyEvent.VK_Z);

    //charToVKeyMap.put('A',KeyEvent.VK_A);
    //charToVKeyMap.put('B',KeyEvent.VK_B);
    //charToVKeyMap.put('C',KeyEvent.VK_C);
    //charToVKeyMap.put('D',KeyEvent.VK_D);
    //charToVKeyMap.put('E',KeyEvent.VK_E);
    //charToVKeyMap.put('F',KeyEvent.VK_F);
    //charToVKeyMap.put('G',KeyEvent.VK_G);
    //charToVKeyMap.put('H',KeyEvent.VK_H);
    //charToVKeyMap.put('I',KeyEvent.VK_I);
    //charToVKeyMap.put('J',KeyEvent.VK_J);
    //charToVKeyMap.put('K',KeyEvent.VK_K);
    //charToVKeyMap.put('L',KeyEvent.VK_L);
    //charToVKeyMap.put('M',KeyEvent.VK_M);
    //charToVKeyMap.put('N',KeyEvent.VK_N);
    //charToVKeyMap.put('O',KeyEvent.VK_O);
    //charToVKeyMap.put('P',KeyEvent.VK_P);
    //charToVKeyMap.put('Q',KeyEvent.VK_Q);
    //charToVKeyMap.put('R',KeyEvent.VK_R);
    //charToVKeyMap.put('S',KeyEvent.VK_S);
    //charToVKeyMap.put('T',KeyEvent.VK_T);
    //charToVKeyMap.put('U',KeyEvent.VK_U);
    //charToVKeyMap.put('V',KeyEvent.VK_V);
    //charToVKeyMap.put('W',KeyEvent.VK_W);
    //charToVKeyMap.put('X',KeyEvent.VK_X);
    //charToVKeyMap.put('Y',KeyEvent.VK_Y);
    //charToVKeyMap.put('Z',KeyEvent.VK_Z);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD0);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD1);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD2);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD3);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD4);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD5);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD6);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD7);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD8);
    //charToVKeyMap.put('',KeyEvent.VK_NUMPAD9);
    //charToVKeyMap.put('',KeyEvent.VK_WINDOWS);
    //charToVKeyMap.put('',KeyEvent.VK_CONTEXT_MENU);
    //charToVKeyMap.put('',KeyEvent.VK_CIRCUMFLEX);
  }
}

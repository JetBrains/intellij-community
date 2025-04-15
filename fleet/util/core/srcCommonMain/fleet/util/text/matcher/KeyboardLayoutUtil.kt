// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.util.Os

/**
 * @author gregsh
 */
object KeyboardLayoutUtil {
  private val ourLLtoASCII = ConcurrentHashMap<Char, Char>()

  fun getAsciiForChar(a: Char): Char? {
    var c = ourLLtoASCII.get(a)
    if (c != null) return c

    if (ourLLtoASCII.isEmpty() || Os.INSTANCE.isLinux) {
      // Linux note:
      // KeyEvent provides 'rawCode' (a physical |row|column| coordinate) instead of 'keyCode'.
      // ASCII rawCodes can be collected to map chars via their rawCode in future.
      // That would also allow map latin chars to latin chars when a layout switches latin keys.
      val lc = a.lowercaseChar()
      c = HardCoded.LL.get(lc)
      if (c == null) return null
      return if (lc == a) c else c.uppercaseChar()
    }
    return null
  }

  fun storeAsciiForChar(keyCode: Int, keyChar: Char, asciiFirstKeyCode: Int, asciiLastKeyCode: Int) {
    if (keyCode < asciiFirstKeyCode || asciiLastKeyCode < keyCode) return
    if ('a' <= keyChar && keyChar <= 'z' || 'A' <= keyChar && keyChar <= 'Z') return
    if (ourLLtoASCII.containsKey(keyChar)) return

    var converted = ('a'.code + (keyCode - asciiFirstKeyCode)).toChar()
    if (keyChar.isUpperCase()) {
      converted = converted.uppercaseChar()
    }
    ourLLtoASCII.put(keyChar, converted)
  }

  private object HardCoded {
    val LL = HashMap<Char, Char>(33)

    init {
      // keyboard layouts in lowercase
      val layout = charArrayOf( // Russian-PC
        'й', 'q', 'ц', 'w', 'у', 'e', 'к', 'r', 'е', 't', 'н', 'y', 'г', 'u',
        'ш', 'i', 'щ', 'o', 'з', 'p', 'х', '[', 'ъ', ']', 'ф', 'a', 'ы', 's',
        'в', 'd', 'а', 'f', 'п', 'g', 'р', 'h', 'о', 'j', 'л', 'k', 'д', 'l',
        'ж', ';', 'э', '\'', 'я', 'z', 'ч', 'x', 'с', 'c', 'м', 'v', 'и', 'b',
        'т', 'n', 'ь', 'm', 'б', ',', 'ю', '.', '.', '/'
      )
      var i = 0
      while (i < layout.size) {
        LL.put(layout[i++], layout[i++])
      }
    }
  }
}

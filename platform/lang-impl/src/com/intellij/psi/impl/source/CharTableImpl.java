/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;

/**
 * @author max
 */
public class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.
  private static final CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();
  private static final OpenTHashSet<CharSequence> STATIC_ENTRIES = newStaticSet();

  private final OpenTHashSet<CharSequence> entries = new OpenTHashSet<CharSequence>(10, 0.9f, HASHER);

  public CharSequence intern(final CharSequence text) {
    if (text.length() > INTERN_THRESHOLD) return createSequence(text);

    int idx = STATIC_ENTRIES.index(text);
    if (idx >= 0) {
      return STATIC_ENTRIES.get(idx);
    }

    synchronized(entries) {
      idx = entries.index(text);
      if (idx >= 0) {
        return entries.get(idx);
      }

      // We need to create separate string just to prevent referencing all character data when original is string or char sequence over string
      final CharSequence entry = createSequence(text);
      boolean added = entries.add(entry);
      assert added;

      return entry;
    }
  }

  public CharSequence intern(final CharSequence baseText, final int startOffset, final int endOffset) {
    if (endOffset - startOffset == baseText.length()) return baseText.toString();
    return intern(baseText.subSequence(startOffset, endOffset));
  }

  private static CharSequence createSequence(final CharSequence text) {
    final char[] buf = new char[text.length()];
    CharArrayUtil.getChars(text, buf, 0);
    return new CharArrayCharSequence(buf);
  }

  public static void staticIntern(final String text) {
    synchronized(STATIC_ENTRIES) {
      STATIC_ENTRIES.add(text);
    }
  }
  
  private static OpenTHashSet<CharSequence> newStaticSet() {
    final OpenTHashSet<CharSequence> r = new OpenTHashSet<CharSequence>(10, 0.9f, HASHER);
    r.add("==" );
    r.add("!=" );
    r.add("||" );
    r.add("++" );
    r.add("--" );

    r.add("<" );
    r.add("<=" );
    r.add("<<=" );
    r.add("<<" );
    r.add(">" );
    r.add("&" );
    r.add("&&" );

    r.add("+=" );
    r.add("-=" );
    r.add("*=" );
    r.add("/=" );
    r.add("&=" );
    r.add("|=" );
    r.add("^=" );
    r.add("%=" );

    r.add("("   );
    r.add(")"   );
    r.add("{"   );
    r.add("}"   );
    r.add("["   );
    r.add("]"   );
    r.add(";"   );
    r.add(","   );
    r.add("..." );
    r.add("."   );

    r.add("=" );
    r.add("!" );
    r.add("~" );
    r.add("?" );
    r.add(":" );
    r.add("+" );
    r.add("-" );
    r.add("*" );
    r.add("/" );
    r.add("|" );
    r.add("^" );
    r.add("%" );
    r.add("@" );

    r.add(" " );
    r.add("  " );
    r.add("   " );
    r.add("    " );
    r.add("     " );
    r.add("      " );
    r.add("       " );
    r.add("        " );
    r.add("         " );
    r.add("          " );
    r.add("           " );
    r.add("            " );
    r.add("             " );
    r.add("              " );
    r.add("               " );
    r.add("\n" );
    r.add("\n  " );
    r.add("\n    " );
    r.add("\n      " );
    r.add("\n        " );
    r.add("\n          " );
    r.add("\n            " );
    r.add("\n              " );
    r.add("\n                " );

    r.add("<");
    r.add(">");
    r.add("</");
    r.add("/>");
    r.add("\"");
    r.add("\'");
    r.add("<![CDATA[");
    r.add("]]>");
    r.add("<!--");
    r.add("-->");
    r.add("<!DOCTYPE");
    r.add("SYSTEM");
    r.add("PUBLIC");
    r.add("<?");
    r.add("?>");

    r.add("<%");
    r.add("%>");
    r.add("<%=");
    r.add("<%@");
    r.add("${");
    r.add("");
    return r;
  }
}

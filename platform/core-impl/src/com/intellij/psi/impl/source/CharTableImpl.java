/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.CharTable;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringFactory;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author max
 */
public class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.

  private static final StringHashToCharSequencesMap STATIC_ENTRIES = newStaticSet();
  private final StringHashToCharSequencesMap entries = new StringHashToCharSequencesMap(10, 0.9f);

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence text) {
    return text.length() > INTERN_THRESHOLD ? createSequence(text) : doIntern(text);
  }

  @NotNull
  private CharSequence doIntern(@NotNull CharSequence text, int startOffset, int endOffset) {
    int hashCode = subSequenceHashCode(text, startOffset, endOffset);
    CharSequence interned = STATIC_ENTRIES.getSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    if (interned != null) {
      return interned;
    }

    synchronized(entries) {
      // We need to create separate string just to prevent referencing all character data when original is string or char sequence over string
      return entries.getOrAddSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    }
  }

  @NotNull
  public CharSequence doIntern(@NotNull CharSequence text) {
    return doIntern(text, 0, text.length());
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence baseText, final int startOffset, final int endOffset) {
    CharSequence result;
    if (endOffset - startOffset == baseText.length()) result = intern(baseText);
    else if (endOffset - startOffset > INTERN_THRESHOLD) result = createSequence(baseText, startOffset, endOffset);
    else result = doIntern(baseText, startOffset, endOffset);

    return result;
  }

  @NotNull
  private static String createSequence(@NotNull CharSequence text) {
    return createSequence(text, 0, text.length());
  }

  @NotNull
  private static String createSequence(@NotNull CharSequence text, int startOffset, int endOffset) {
    if (text instanceof String) {
      return ((String)text).substring(startOffset, endOffset);
    }
    char[] buf = new char[endOffset - startOffset];
    CharArrayUtil.getChars(text, buf, startOffset, 0, buf.length);
    return StringFactory.createShared(buf); // this way the .toString() doesn't create another instance (as opposed to new CharArrayCharSequence())
  }

  @Nullable
  public static CharSequence getStaticInterned(@NotNull CharSequence text) {
    return STATIC_ENTRIES.get(text);
  }

  public static void staticIntern(@NotNull String text) {
    synchronized(STATIC_ENTRIES) {
      STATIC_ENTRIES.add(text);
    }
  }

  private static StringHashToCharSequencesMap newStaticSet() {
    final StringHashToCharSequencesMap r = new StringHashToCharSequencesMap(10, 0.9f);
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

  static {
    addStringsFromClassToStatics(CommonClassNames.class);
  }
  public static void addStringsFromClassToStatics(@NotNull Class aClass) {
    for (Field field : aClass.getDeclaredFields()) {
      if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
      if ((field.getModifiers() & Modifier.PUBLIC) == 0) continue;
      String typeName = ReflectionUtil.getStaticFieldValue(aClass, String.class, field.getName());
      if (typeName != null) {
        staticIntern(typeName);
      }
    }
  }

  private static class StringHashToCharSequencesMap extends TIntObjectHashMap<Object> {
    private StringHashToCharSequencesMap(int capacity, float loadFactor) {
      super(capacity, loadFactor);
    }

    private CharSequence get(CharSequence sequence, int startOffset, int endOffset) {
      return getSubSequenceWithHashCode(subSequenceHashCode(sequence, startOffset, endOffset), sequence, startOffset, endOffset);
    }

    private CharSequence getSubSequenceWithHashCode(int hashCode, CharSequence sequence, int startOffset, int endOffset) {
      Object o = get(hashCode);
      if (o == null) return null;
      if (o instanceof CharSequence) {
        if (charSequenceSubSequenceEquals((CharSequence)o, sequence, startOffset, endOffset)) {
          return (CharSequence)o;
        }
        return null;
      }
      if (o instanceof CharSequence[]) {
        for(CharSequence cs:(CharSequence[])o) {
          if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
            return cs;
          }
        }
        return null;
      }
      assert false:o.getClass();
      return null;
    }

    private static boolean charSequenceSubSequenceEquals(CharSequence cs, CharSequence baseSequence, int startOffset, int endOffset) {
      if (cs.length() != endOffset - startOffset) return false;
      if (cs == baseSequence && startOffset == 0) return true;
      for(int i = 0, len = cs.length(); i < len; ++i) {
        if (cs.charAt(i) != baseSequence.charAt(startOffset + i)) return false;
      }
      return true;
    }

    private CharSequence get(CharSequence sequence) {
      return get(sequence, 0, sequence.length());
    }

    private CharSequence add(CharSequence sequence) {
      return add(sequence, 0, sequence.length());
    }

    private CharSequence add(CharSequence sequence, int startOffset, int endOffset) {
      int hashCode = subSequenceHashCode(sequence, startOffset, endOffset);
      return getOrAddSubSequenceWithHashCode(hashCode, sequence, startOffset, endOffset);
    }

    private CharSequence getOrAddSubSequenceWithHashCode(int hashCode, CharSequence sequence, int startOffset, int endOffset) {
      int index = index(hashCode);
      String addedSequence = null;

      if (index < 0) {
        put(hashCode, addedSequence = createSequence(sequence, startOffset, endOffset));
      } else {
        Object value = _values[index];
        if (value instanceof CharSequence) {
          CharSequence existingSequence = (CharSequence)value;
          if (charSequenceSubSequenceEquals(existingSequence, sequence, startOffset, endOffset)) {
            return existingSequence;
          }
          put(hashCode, new CharSequence[] {existingSequence, addedSequence = createSequence(sequence, startOffset, endOffset)});
        } else if (value instanceof CharSequence[]) {
          CharSequence[] existingSequenceArray = (CharSequence[])value;
          for(CharSequence cs:existingSequenceArray) {
            if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
              return cs;
            }
          }
          CharSequence[] newSequenceArray = new CharSequence[existingSequenceArray.length + 1];
          System.arraycopy(existingSequenceArray, 0, newSequenceArray, 0, existingSequenceArray.length);
          newSequenceArray[existingSequenceArray.length] = addedSequence = createSequence(sequence, startOffset, endOffset);
          put(hashCode, newSequenceArray);
        } else {
          assert false:value.getClass();
        }
      }
      return addedSequence;
    }
  }

  private static int subSequenceHashCode(CharSequence sequence, int startOffset, int endOffset) {
    if (startOffset == 0 && endOffset == sequence.length()) {
      return StringUtil.stringHashCode(sequence);
    }
    return StringUtil.stringHashCode(sequence, startOffset, endOffset);
  }
}

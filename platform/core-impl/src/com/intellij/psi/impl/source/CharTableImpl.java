// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringFactory;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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

  @NotNull
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
      if (!String.class.equals(field.getType())) continue;
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

    private CharSequence get(@NotNull CharSequence sequence, int startOffset, int endOffset) {
      return getSubSequenceWithHashCode(subSequenceHashCode(sequence, startOffset, endOffset), sequence, startOffset, endOffset);
    }

    private CharSequence getSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
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

    private static boolean charSequenceSubSequenceEquals(@NotNull CharSequence cs, @NotNull CharSequence baseSequence, int startOffset, int endOffset) {
      if (cs.length() != endOffset - startOffset) return false;
      if (cs == baseSequence && startOffset == 0) return true;
      for(int i = 0, len = cs.length(); i < len; ++i) {
        if (cs.charAt(i) != baseSequence.charAt(startOffset + i)) return false;
      }
      return true;
    }

    private CharSequence get(@NotNull CharSequence sequence) {
      return get(sequence, 0, sequence.length());
    }

    @NotNull
    private CharSequence add(@NotNull CharSequence sequence) {
      return add(sequence, 0, sequence.length());
    }

    @NotNull
    private CharSequence add(CharSequence sequence, int startOffset, int endOffset) {
      int hashCode = subSequenceHashCode(sequence, startOffset, endOffset);
      return getOrAddSubSequenceWithHashCode(hashCode, sequence, startOffset, endOffset);
    }

    @NotNull
    private CharSequence getOrAddSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
      int index = index(hashCode);
      String addedSequence;

      if (index < 0) {
        addedSequence = createSequence(sequence, startOffset, endOffset);
        put(hashCode, addedSequence);
      }
      else {
        Object value = _values[index];
        if (value instanceof CharSequence) {
          CharSequence existingSequence = (CharSequence)value;
          if (charSequenceSubSequenceEquals(existingSequence, sequence, startOffset, endOffset)) {
            return existingSequence;
          }
          addedSequence = createSequence(sequence, startOffset, endOffset);
          put(hashCode, new CharSequence[]{existingSequence, addedSequence});
        }
        else if (value instanceof CharSequence[]) {
          CharSequence[] existingSequenceArray = (CharSequence[])value;
          for (CharSequence cs : existingSequenceArray) {
            if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
              return cs;
            }
          }
          addedSequence = createSequence(sequence, startOffset, endOffset);
          CharSequence[] newSequenceArray = ArrayUtil.append(existingSequenceArray, addedSequence, CharSequence[]::new);
          put(hashCode, newSequenceArray);
        }
        else {
          assert false : value.getClass();
          return null;
        }
      }
      return addedSequence;
    }
  }

  private static int subSequenceHashCode(@NotNull CharSequence sequence, int startOffset, int endOffset) {
    if (startOffset == 0 && endOffset == sequence.length()) {
      return StringUtil.stringHashCode(sequence);
    }
    return StringUtil.stringHashCode(sequence, startOffset, endOffset);
  }
}

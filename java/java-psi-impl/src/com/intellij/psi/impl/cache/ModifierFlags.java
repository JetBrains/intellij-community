// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.BitUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Constants used in Java stubs; may differ from ones used in a .class file format.
 *
 * @author max
 */
public final class ModifierFlags {
  public static final int PUBLIC_MASK = 0x0001;
  public static final int PRIVATE_MASK = 0x0002;
  public static final int PROTECTED_MASK = 0x0004;
  public static final int STATIC_MASK = 0x0008;
  public static final int FINAL_MASK = 0x0010;
  public static final int SYNCHRONIZED_MASK = 0x0020;
  public static final int VOLATILE_MASK = 0x0040;
  public static final int TRANSIENT_MASK = 0x0080;
  public static final int NATIVE_MASK = 0x0100;
  public static final int DEFAULT_MASK = 0x0200;
  public static final int ABSTRACT_MASK = 0x0400;
  public static final int STRICTFP_MASK = 0x0800;
  public static final int PACKAGE_LOCAL_MASK = 0x1000;
  public static final int OPEN_MASK = 0x2000;
  public static final int TRANSITIVE_MASK = 0x4000;
  public static final int SEALED_MASK = 0x8000;
  public static final int NON_SEALED_MASK = 0x10000;

  public static final Object2IntMap<String> NAME_TO_MODIFIER_FLAG_MAP = new Object2IntOpenHashMap<>();
  public static final Int2ObjectMap<String> MODIFIER_FLAG_TO_NAME_MAP = new Int2ObjectOpenHashMap<>();
  public static final Object2IntMap<IElementType> KEYWORD_TO_MODIFIER_FLAG_MAP = new Object2IntOpenHashMap<>();
  static {
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PUBLIC, PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PRIVATE, PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PROTECTED, PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STATIC, STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.FINAL, FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.SYNCHRONIZED, SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.VOLATILE, VOLATILE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.TRANSIENT, TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.NATIVE, NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.DEFAULT, DEFAULT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.ABSTRACT, ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STRICTFP, STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PACKAGE_LOCAL, PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.OPEN, OPEN_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.TRANSITIVE, TRANSITIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.SEALED, SEALED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.NON_SEALED, NON_SEALED_MASK);

    for (Object name : NAME_TO_MODIFIER_FLAG_MAP.keySet()) {
      MODIFIER_FLAG_TO_NAME_MAP.put(NAME_TO_MODIFIER_FLAG_MAP.getInt(name), (String)name);
    }

    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PUBLIC_KEYWORD, PUBLIC_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PRIVATE_KEYWORD, PRIVATE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PROTECTED_KEYWORD, PROTECTED_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.STATIC_KEYWORD, STATIC_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.FINAL_KEYWORD, FINAL_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.SYNCHRONIZED_KEYWORD, SYNCHRONIZED_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.VOLATILE_KEYWORD, VOLATILE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.TRANSIENT_KEYWORD, TRANSIENT_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.NATIVE_KEYWORD, NATIVE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.DEFAULT_KEYWORD, DEFAULT_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.ABSTRACT_KEYWORD, ABSTRACT_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.STRICTFP_KEYWORD, STRICTFP_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.OPEN_KEYWORD, OPEN_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.TRANSITIVE_KEYWORD, TRANSITIVE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.SEALED_KEYWORD, SEALED_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.NON_SEALED_KEYWORD, NON_SEALED_MASK);
  }

  public static boolean hasModifierProperty(String name, int mask) {
    int flag = NAME_TO_MODIFIER_FLAG_MAP.getInt(name);
    assert flag != 0 : name;
    return BitUtil.isSet(mask, flag);
  }
}

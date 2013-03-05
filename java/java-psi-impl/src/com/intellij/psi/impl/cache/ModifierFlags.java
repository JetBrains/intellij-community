/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TObjectIntHashMap;

/**
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
  public static final int DEFENDER_MASK = 0x0200;
  public static final int ABSTRACT_MASK = 0x0400;
  public static final int STRICTFP_MASK = 0x0800;
  public static final int PACKAGE_LOCAL_MASK = 0x1000;

  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();
  public static final TObjectIntHashMap<IElementType> KEYWORD_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<IElementType>();
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
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.DEFAULT, DEFENDER_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.ABSTRACT, ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STRICTFP, STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PACKAGE_LOCAL, PACKAGE_LOCAL_MASK);

    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PUBLIC_KEYWORD, PUBLIC_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PRIVATE_KEYWORD, PRIVATE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.PROTECTED_KEYWORD, PROTECTED_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.STATIC_KEYWORD, STATIC_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.FINAL_KEYWORD, FINAL_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.SYNCHRONIZED_KEYWORD, SYNCHRONIZED_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.VOLATILE_KEYWORD, VOLATILE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.TRANSIENT_KEYWORD, TRANSIENT_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.NATIVE_KEYWORD, NATIVE_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.DEFAULT_KEYWORD, DEFENDER_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.ABSTRACT_KEYWORD, ABSTRACT_MASK);
    KEYWORD_TO_MODIFIER_FLAG_MAP.put(JavaTokenType.STRICTFP_KEYWORD, STRICTFP_MASK);
  }

  public static boolean hasModifierProperty(String name, int mask) {
    int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
    assert flag != 0 : name;
    return (mask & flag) != 0;
  }
}

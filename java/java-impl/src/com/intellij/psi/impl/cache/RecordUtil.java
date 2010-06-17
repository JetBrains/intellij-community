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
package com.intellij.psi.impl.cache;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author max
 */
public class RecordUtil {
  @NonNls private static final String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  @NonNls private static final String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() {}

  public static boolean isDeprecatedByAnnotation(PsiElement element) {
    if (element instanceof PsiModifierListOwner) {
      PsiModifierList modifierList = ((PsiModifierListOwner)element).getModifierList();
      if (modifierList != null) {
        PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
          PsiJavaCodeReferenceElement nameElement = annotation.getNameReferenceElement();
          if (nameElement != null && DEPRECATED_ANNOTATION_NAME.equals(nameElement.getReferenceName())) return true;
        }
      }
    }

    return false;
  }


  public static int packModifierList(final PsiModifierList psiModifierList) {
    int packed = 0;

    if (psiModifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
      packed |= ModifierFlags.ABSTRACT_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.FINAL)) {
      packed |= ModifierFlags.FINAL_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.NATIVE)) {
      packed |= ModifierFlags.NATIVE_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.STATIC)) {
      packed |= ModifierFlags.STATIC_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      packed |= ModifierFlags.SYNCHRONIZED_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
      packed |= ModifierFlags.TRANSIENT_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
      packed |= ModifierFlags.VOLATILE_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      packed |= ModifierFlags.PRIVATE_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      packed |= ModifierFlags.PROTECTED_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      packed |= ModifierFlags.PUBLIC_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      packed |= ModifierFlags.PACKAGE_LOCAL_MASK;
    }
    if (psiModifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
      packed |= ModifierFlags.STRICTFP_MASK;
    }
    return packed;
  }

  public static boolean isDeprecatedByDocComment(PsiElement psiElement) {
    if (!(psiElement instanceof PsiDocCommentOwner)) return false;

    final PsiDocCommentOwner owner = (PsiDocCommentOwner)psiElement;
    if (owner instanceof PsiCompiledElement) {
      return owner.isDeprecated();
    }

    final ASTNode node = psiElement.getNode();
    final ASTNode docNode = node.findChildByType(JavaDocElementType.DOC_COMMENT);
    if (docNode == null || docNode.getText().indexOf(DEPRECATED_TAG) < 0) return false;

    PsiDocComment docComment = owner.getDocComment();
    return docComment != null && docComment.findTagByName("deprecated") != null;
  }


  private static final TObjectIntHashMap<String> ourModifierNameToFlagMap;

  static {
    ourModifierNameToFlagMap = new TObjectIntHashMap<String>();

    initMaps();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initMaps() {
    ourModifierNameToFlagMap.put(PsiModifier.PUBLIC, ModifierFlags.PUBLIC_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PROTECTED, ModifierFlags.PROTECTED_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PRIVATE, ModifierFlags.PRIVATE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.PACKAGE_LOCAL, ModifierFlags.PACKAGE_LOCAL_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.STATIC, ModifierFlags.STATIC_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.ABSTRACT, ModifierFlags.ABSTRACT_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.FINAL, ModifierFlags.FINAL_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.NATIVE, ModifierFlags.NATIVE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.SYNCHRONIZED, ModifierFlags.SYNCHRONIZED_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.TRANSIENT, ModifierFlags.TRANSIENT_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.VOLATILE, ModifierFlags.VOLATILE_MASK);
    ourModifierNameToFlagMap.put(PsiModifier.STRICTFP, ModifierFlags.STRICTFP_MASK);
    ourModifierNameToFlagMap.put("interface", ModifierFlags.INTERFACE_MASK);
    ourModifierNameToFlagMap.put("deprecated", ModifierFlags.DEPRECATED_MASK);
    ourModifierNameToFlagMap.put("@Deprecated", ModifierFlags.ANNOTATION_DEPRECATED_MASK);
    ourModifierNameToFlagMap.put("enum", ModifierFlags.ENUM_MASK);
    ourModifierNameToFlagMap.put("@", ModifierFlags.ANNOTATION_TYPE_MASK);
  }

  public static boolean hasModifierProperty(String psiModifier, int packed) {
    return (ourModifierNameToFlagMap.get(psiModifier) & packed) != 0;
  }

  private static final Set<String>[] SET_INSTANCES = new Set[8 * 4];

  private static final String[] VISIBILITY_MODIFIERS = {null, PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED};

  private static final int[] MODIFIER_MAP = {0, 1, 2, -1, 3, -1, -1, -1, -1};

  static {
    SET_INSTANCES[0] = Collections.emptySet();
    for (int i = 1; i < 4; i++) {
      SET_INSTANCES[i << 3] = Collections.singleton(VISIBILITY_MODIFIERS[i]);
    }

    for (int i = 1; i < 8; i++) {
      int attr = i << 3;

      Set<String> set = new LinkedHashSet<String>();
      if ((attr & ModifierFlags.STATIC_MASK) != 0) set.add(PsiModifier.STATIC);
      if ((attr & ModifierFlags.FINAL_MASK) != 0) set.add(PsiModifier.FINAL);
      if ((attr & (4 << 3)) != 0) set.add(PsiModifier.ABSTRACT);

      if (set.size() == 1) set = Collections.singleton(set.iterator().next());

      SET_INSTANCES[i] = set;

      for (int k = 1; k < 4; k++) {
        Set<String> setWithModifier = new LinkedHashSet<String>();
        setWithModifier.add(VISIBILITY_MODIFIERS[k]);
        setWithModifier.addAll(set);
        assert setWithModifier.size() > 1;

        SET_INSTANCES[(k << 3) + i] = setWithModifier;
      }
    }
  }

  public static Set<String> getModifierSet(int modifiers) {
    assert (modifiers & ~(ModifierFlags.PUBLIC_MASK | ModifierFlags.PRIVATE_MASK | ModifierFlags.PROTECTED_MASK |
                          ModifierFlags.FINAL_MASK | ModifierFlags.ABSTRACT_MASK | ModifierFlags.STATIC_MASK)) == 0;

    int visibilityModifierIndex = MODIFIER_MAP[modifiers & 7];
    int index = ((modifiers >>> 3) & 3) + ((modifiers & ModifierFlags.ABSTRACT_MASK) >>> 8);
    if (visibilityModifierIndex != -1) {
      return SET_INSTANCES[index + (visibilityModifierIndex << 3)];
    }

    Set<String> res = new LinkedHashSet<String>();
    if ((modifiers & ModifierFlags.PUBLIC_MASK) != 0) res.add(PsiModifier.PUBLIC);
    if ((modifiers & ModifierFlags.PRIVATE_MASK) != 0) res.add(PsiModifier.PRIVATE);
    if ((modifiers & ModifierFlags.PROTECTED_MASK) != 0) res.add(PsiModifier.PROTECTED);

    res.addAll(SET_INSTANCES[index]);

    return res;
  }

}

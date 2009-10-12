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


}

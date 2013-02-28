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
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class RecordUtil {
  @NonNls private static final String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  @NonNls private static final String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() { }

  public static boolean isDeprecatedByAnnotation(@NotNull LighterAST tree, @NotNull LighterASTNode modList) {
    for (final LighterASTNode child : tree.getChildren(modList)) {
      if (child.getTokenType() == JavaElementType.ANNOTATION) {
        final LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, child, JavaElementType.JAVA_CODE_REFERENCE);
        if (ref != null) {
          final LighterASTNode id = LightTreeUtil.firstChildOfType(tree, ref, JavaTokenType.IDENTIFIER);
          if (id != null) {
            final String name = intern(tree.getCharTable(), id);
            if (DEPRECATED_ANNOTATION_NAME.equals(name)) return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isDeprecatedByDocComment(@NotNull LighterAST tree, @NotNull LighterASTNode comment) {
    // todo[r.sh] parse doc comments, implement tree lookup
    String text = LightTreeUtil.toFilteredString(tree, comment, null);
    return text.contains(DEPRECATED_TAG);
  }

  public static int packModifierList(@NotNull LighterAST tree, @NotNull LighterASTNode modList, @NotNull StubElement parent) {
    int packed = 0;

    final LighterASTNode modListOwner = tree.getParent(modList);
    if (modListOwner != null && modListOwner.getTokenType() == parent.getStubType()) {
      final StubElement grandParent = parent.getParentStub();
      if (parent instanceof PsiClassStub) {
        if (grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          packed |= ModifierFlags.PUBLIC_MASK;
          packed |= ModifierFlags.STATIC_MASK;
        }
        if (((PsiClassStub)parent).isInterface()) {
          packed |= ModifierFlags.ABSTRACT_MASK;
          if (grandParent instanceof PsiClassStub) {
            packed |= ModifierFlags.STATIC_MASK;
          }
        }
        else if (((PsiClassStub)parent).isEnum()) {
          if (!(grandParent instanceof PsiFileStub)) {
            packed |= ModifierFlags.STATIC_MASK;
          }

          boolean isFinal = true;
          final List<LighterASTNode> enumConstants = LightTreeUtil.getChildrenOfType(tree, modListOwner, JavaElementType.ENUM_CONSTANT);
          for (final LighterASTNode constant : enumConstants) {
            if (LightTreeUtil.firstChildOfType(tree, constant, JavaElementType.ENUM_CONSTANT_INITIALIZER) != null) {
              isFinal = false;
              break;
            }
          }
          if (isFinal) {
            packed |= ModifierFlags.FINAL_MASK;
          }

          final List<LighterASTNode> methods = LightTreeUtil.getChildrenOfType(tree, modListOwner, JavaElementType.METHOD);
          for (final LighterASTNode method : methods) {
            final LighterASTNode mods = LightTreeUtil.requiredChildOfType(tree, method, JavaElementType.MODIFIER_LIST);
            if (LightTreeUtil.firstChildOfType(tree, mods, JavaTokenType.ABSTRACT_KEYWORD) != null) {
              packed |= ModifierFlags.ABSTRACT_MASK;
              break;
            }
          }
        }
      }
      else if (parent instanceof PsiMethodStub) {
        if (grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          packed |= ModifierFlags.PUBLIC_MASK;
          packed |= ModifierFlags.ABSTRACT_MASK;
        }
      }
      else if (parent instanceof PsiFieldStub) {
        if (parent.getStubType() == JavaElementType.ENUM_CONSTANT ||
            grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          packed |= ModifierFlags.PUBLIC_MASK;
          packed |= ModifierFlags.STATIC_MASK;
          packed |= ModifierFlags.FINAL_MASK;
        }
      }
    }

    for (final LighterASTNode child : tree.getChildren(modList)) {
      final int flag = ModifierFlags.KEYWORD_TO_MODIFIER_FLAG_MAP.get(child.getTokenType());
      if (flag != 0) {
        packed |= flag;
      }
    }

    if ((packed & ModifierFlags.DEFENDER_MASK) != 0) {
      packed &= ~ModifierFlags.ABSTRACT_MASK;
    }

    if ((packed & (ModifierFlags.PRIVATE_MASK | ModifierFlags.PROTECTED_MASK | ModifierFlags.PUBLIC_MASK)) == 0) {
      packed |= ModifierFlags.PACKAGE_LOCAL_MASK;
    }

    return packed;
  }

  @NotNull
  public static String intern(@NotNull CharTable table, @NotNull LighterASTNode node) {
    assert node instanceof LighterASTTokenNode : node;
    return table.intern(((LighterASTTokenNode)node).getText()).toString();
  }

  public static boolean isStaticNonPrivateMember(@NotNull StubElement<?> stub) {
    StubElement<PsiModifierList> type = stub.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    if (!(type instanceof PsiModifierListStub)) {
      return false;
    }

    int mask = ((PsiModifierListStub)type).getModifiersMask();
    return ModifierFlags.hasModifierProperty(PsiModifier.STATIC, mask) && !ModifierFlags.hasModifierProperty(PsiModifier.PRIVATE, mask);
  }
}

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

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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

  public static boolean isDeprecatedByAnnotation(final LighterAST tree, final LighterASTNode modList) {
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

  public static int packModifierList(final LighterAST tree, final LighterASTNode modList, final StubElement parent) {
    int packed = 0;
    boolean alreadyPublic = false;
    boolean alreadyStatic = false;
    boolean alreadyFinal = false;
    boolean alreadyAbstract = false;

    final LighterASTNode modListOwner = tree.getParent(modList);
    if (modListOwner != null && modListOwner.getTokenType() == parent.getStubType()) {
      final StubElement grandParent = parent.getParentStub();
      if (parent instanceof PsiClassStub) {
        if (grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          alreadyPublic = true;
          alreadyStatic = true;
        }
        if (((PsiClassStub)parent).isInterface()) {
          alreadyAbstract = true;
          alreadyStatic = grandParent instanceof PsiClassStub;
        }
        if (((PsiClassStub)parent).isEnum()) {
          alreadyStatic = !(grandParent instanceof PsiFileStub);

          alreadyFinal = true;
          final List<LighterASTNode> enumConstants = LightTreeUtil.getChildrenOfType(tree, modListOwner, JavaElementType.ENUM_CONSTANT);
          for (final LighterASTNode constant : enumConstants) {
            if (LightTreeUtil.firstChildOfType(tree, constant, JavaElementType.ENUM_CONSTANT_INITIALIZER) != null) {
              alreadyFinal = false;
              break;
            }
          }

          alreadyAbstract = false;
          final List<LighterASTNode> methods = LightTreeUtil.getChildrenOfType(tree, modListOwner, JavaElementType.METHOD);
          for (final LighterASTNode method : methods) {
            final LighterASTNode mods = LightTreeUtil.requiredChildOfType(tree, method, JavaElementType.MODIFIER_LIST);
            if (LightTreeUtil.firstChildOfType(tree, mods, JavaTokenType.ABSTRACT_KEYWORD) != null) {
              alreadyAbstract = true;
              break;
            }
          }
        }
      }
      else if (parent instanceof PsiMethodStub) {
        if (grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          alreadyPublic = true;
          alreadyAbstract = true;
        }
      }
      else if (parent instanceof PsiFieldStub) {
        if (parent.getStubType() == JavaElementType.ENUM_CONSTANT) {
          alreadyPublic = true;
          alreadyStatic = true;
          alreadyFinal = true;
        }
        else if (grandParent instanceof PsiClassStub && ((PsiClassStub)grandParent).isInterface()) {
          alreadyPublic = true;
          alreadyStatic = true;
          alreadyFinal = true;
        }
      }
    }

    for (final LighterASTNode child : tree.getChildren(modList)) {
      final IElementType type = child.getTokenType();

      if(type == JavaTokenType.PUBLIC_KEYWORD) {
        alreadyPublic = true;
      } else if(type == JavaTokenType.PRIVATE_KEYWORD) {
        packed |= ModifierFlags.PRIVATE_MASK;
      } else if(type == JavaTokenType.PROTECTED_KEYWORD) {
        packed |= ModifierFlags.PROTECTED_MASK;
      } else if(type == JavaTokenType.ABSTRACT_KEYWORD) {
        alreadyAbstract =true;
      } else if (type == JavaTokenType.FINAL_KEYWORD) {
        alreadyFinal = true;
      } else if (type == JavaTokenType.STATIC_KEYWORD) {
        alreadyStatic = true;
      } else if(type == JavaTokenType.NATIVE_KEYWORD) {
        packed |= ModifierFlags.NATIVE_MASK;
      } else if (type == JavaTokenType.SYNCHRONIZED_KEYWORD) {
        packed |= ModifierFlags.SYNCHRONIZED_MASK;
      } else if (type == JavaTokenType.TRANSIENT_KEYWORD) {
        packed |= ModifierFlags.TRANSIENT_MASK;
      } else if (type == JavaTokenType.VOLATILE_KEYWORD) {
        packed |= ModifierFlags.VOLATILE_MASK;
      } else if (type == JavaTokenType.STRICTFP_KEYWORD) {
        packed |= ModifierFlags.STRICTFP_MASK;
      }
    }

    if (alreadyAbstract) packed |= ModifierFlags.ABSTRACT_MASK;
    if (alreadyFinal) packed |= ModifierFlags.FINAL_MASK;
    if (alreadyPublic) packed |= ModifierFlags.PUBLIC_MASK;
    if (alreadyStatic) packed |= ModifierFlags.STATIC_MASK;

    if ((packed & ModifierFlags.PRIVATE_MASK) == 0 &&
        (packed & ModifierFlags.PROTECTED_MASK) == 0 &&
        (packed & ModifierFlags.PUBLIC_MASK) == 0
       ) {
      packed |= ModifierFlags.PACKAGE_LOCAL_MASK;
    }

    return packed;
  }

  public static boolean isDeprecatedByDocComment(final LighterAST tree, final LighterASTNode comment) {
    // todo[r.sh] parse doc comments, implement tree lookup
    final String text = LightTreeUtil.toFilteredString(tree, comment, null);
    return text != null && text.contains(DEPRECATED_TAG);
  }

  private static final TObjectIntHashMap<String> ourModifierNameToFlagMap;

  static {
    ourModifierNameToFlagMap = new TObjectIntHashMap<String>();

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

  @SuppressWarnings({"unchecked"})
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

  public static String intern(final CharTable table, final LighterASTNode node) {
    assert node instanceof LighterASTTokenNode;
    return table.intern(((LighterASTTokenNode)node).getText()).toString();
  }
}
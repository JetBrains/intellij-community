/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class PsiModifierListImpl extends JavaStubPsiElement<PsiModifierListStub> implements PsiModifierList {
  private static final Map<String, IElementType> NAME_TO_KEYWORD_TYPE_MAP;
  static {
    NAME_TO_KEYWORD_TYPE_MAP = new THashMap<String, IElementType>();
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PUBLIC, JavaTokenType.PUBLIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PROTECTED, JavaTokenType.PROTECTED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PRIVATE, JavaTokenType.PRIVATE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STATIC, JavaTokenType.STATIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.ABSTRACT, JavaTokenType.ABSTRACT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.FINAL, JavaTokenType.FINAL_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.NATIVE, JavaTokenType.NATIVE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.SYNCHRONIZED, JavaTokenType.SYNCHRONIZED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STRICTFP, JavaTokenType.STRICTFP_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.TRANSIENT, JavaTokenType.TRANSIENT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.VOLATILE, JavaTokenType.VOLATILE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.DEFAULT, JavaTokenType.DEFAULT_KEYWORD);
  }

  public PsiModifierListImpl(final PsiModifierListStub stub) {
    super(stub, JavaStubElementTypes.MODIFIER_LIST);
  }

  public PsiModifierListImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierListStub stub = getStub();
    if (stub != null) {
      return ModifierFlags.hasModifierProperty(name, stub.getModifiersMask());
    }

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    PsiElement parent = getParent();
    if (parent instanceof PsiClass) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiClass && ((PsiClass)grandParent).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD) {
          return true;
        }
        if (type == null /* package-private */) {
          return false;
        }
        if (type == JavaTokenType.STATIC_KEYWORD) {
          return true;
        }
      }
      if (((PsiClass)parent).isInterface()) {
        if (type == JavaTokenType.ABSTRACT_KEYWORD) {
          return true;
        }

        // nested interface is implicitly static
        if (grandParent instanceof PsiClass) {
          if (type == JavaTokenType.STATIC_KEYWORD) {
            return true;
          }
        }
      }
      if (((PsiClass)parent).isEnum()) {
        if (type == JavaTokenType.STATIC_KEYWORD) {
          if (!(grandParent instanceof PsiFile)) return true;
        }
        else if (type == JavaTokenType.FINAL_KEYWORD) {
          final PsiField[] fields = ((PsiClass)parent).getFields();
          for (PsiField field : fields) {
            if (field instanceof PsiEnumConstant && ((PsiEnumConstant)field).getInitializingClass() != null) return false;
          }
          return true;
        }
        else if (type == JavaTokenType.ABSTRACT_KEYWORD) {
          final PsiMethod[] methods = ((PsiClass)parent).getMethods();
          for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
          }
          return false;
        }
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiClass aClass = ((PsiMethod)parent).getContainingClass();
      if (aClass != null && aClass.isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD) {
          return true;
        }
        if (type == null /* package-private */) {
          return false;
        }
        if (type == JavaTokenType.ABSTRACT_KEYWORD) {
          final ASTNode node = getNode();
          return node.findChildByType(JavaTokenType.DEFAULT_KEYWORD) == null && 
                 node.findChildByType(JavaTokenType.STATIC_KEYWORD) == null &&
                 node.findChildByType(JavaTokenType.PRIVATE_KEYWORD) == null;
        }
      }
      else if (aClass != null && aClass.isEnum() && ((PsiMethod)parent).isConstructor()) {
        if (type == JavaTokenType.PRIVATE_KEYWORD) {
          return true;
        }
      }
    }
    else if (parent instanceof PsiField) {
      if (parent instanceof PsiEnumConstant) {
        return type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.STATIC_KEYWORD || type == JavaTokenType.FINAL_KEYWORD;
      }
      else {
        PsiClass aClass = ((PsiField)parent).getContainingClass();
        if (aClass != null && aClass.isInterface()) {
          if (type == JavaTokenType.PUBLIC_KEYWORD) {
            return true;
          }
          if (type == null /* package-private */) {
            return false;
          }
          if (type == JavaTokenType.STATIC_KEYWORD) {
            return true;
          }
          if (type == JavaTokenType.FINAL_KEYWORD) {
            return true;
          }
        }
      }
    }
    else if (parent instanceof PsiParameter) {
      if (type == JavaTokenType.FINAL_KEYWORD && ((PsiParameter)parent).getType() instanceof PsiDisjunctionType) return true;
    }
    else if (parent instanceof PsiResourceVariable) {
      if (type == JavaTokenType.FINAL_KEYWORD) return true;
    }

    if (type == null /* package-private */) {
      return !hasModifierProperty(PsiModifier.PUBLIC) &&
             !hasModifierProperty(PsiModifier.PRIVATE) &&
             !hasModifierProperty(PsiModifier.PROTECTED);
    }

    return getNode().findChildByType(type) != null;
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    final CompositeElement tree = (CompositeElement)getNode();
    final IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);
    return tree.findChildByType(type) != null;
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    checkSetModifierProperty(name, value);

    PsiElement parent = getParent();
    PsiElement grandParent = parent != null ? parent.getParent() : null;
    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);
    CompositeElement treeElement = (CompositeElement)getNode();

    // There is a possible case that parameters list occupies more than one line and its elements are aligned. Modifiers list change
    // changes horizontal position of parameters list start, hence, we need to reformat them in order to preserve alignment.
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      CodeEditUtil.markToReformat(method.getParameterList().getNode(), true);
    }

    if (value) {
      if (type == JavaTokenType.PUBLIC_KEYWORD ||
          type == JavaTokenType.PRIVATE_KEYWORD ||
          type == JavaTokenType.PROTECTED_KEYWORD ||
          type == null /* package-private */) {
        if (type != JavaTokenType.PUBLIC_KEYWORD) {
          setModifierProperty(PsiModifier.PUBLIC, false);
        }
        if (type != JavaTokenType.PRIVATE_KEYWORD) {
          setModifierProperty(PsiModifier.PRIVATE, false);
        }
        if (type != JavaTokenType.PROTECTED_KEYWORD) {
          setModifierProperty(PsiModifier.PROTECTED, false);
        }
        if (type == null) return;
      }

      if (parent instanceof PsiField && grandParent instanceof PsiClass && ((PsiClass)grandParent).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.STATIC_KEYWORD || type == JavaTokenType.FINAL_KEYWORD) return;
      }
      else if (parent instanceof PsiMethod && grandParent instanceof PsiClass && ((PsiClass)grandParent).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.ABSTRACT_KEYWORD) return;
      }
      else if (parent instanceof PsiClass && grandParent instanceof PsiClass && ((PsiClass)grandParent).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD) return;
      }
      else if (parent instanceof PsiAnnotationMethod && grandParent instanceof PsiClass && ((PsiClass)grandParent).isAnnotationType()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.ABSTRACT_KEYWORD) return;
      }

      if (treeElement.findChildByType(type) == null) {
        TreeElement keyword = Factory.createSingleLeafElement(type, name, null, getManager());
        treeElement.addInternal(keyword, keyword, null, null);
      }
    }
    else {
      if (type == null /* package-private */) {
        throw new IncorrectOperationException("Cannot reset package-private modifier."); //?
      }

      ASTNode child = treeElement.findChildByType(type);
      if (child != null) {
        SourceTreeToPsiMap.treeToPsiNotNull(child).delete();
      }
    }
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    CheckUtil.checkWritable(this);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    final PsiAnnotation[] own = getStubOrPsiChildren(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
    final List<PsiAnnotation> ext = PsiAugmentProvider.collectAugments(this, PsiAnnotation.class);
    return ArrayUtil.mergeArrayAndCollection(own, ext, PsiAnnotation.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(this);
    List<PsiAnnotation> filtered = ContainerUtil.findAll(getAnnotations(), new Condition<PsiAnnotation>() {
      @Override
      public boolean value(PsiAnnotation annotation) {
        PsiAnnotation.TargetType target = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
        return target != null && target != PsiAnnotation.TargetType.UNKNOWN;
      }
    });

    return filtered.toArray(new PsiAnnotation[filtered.size()]);
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return (PsiAnnotation)addAfter(JavaPsiFacade.getInstance(getProject()).getElementFactory().createAnnotationFromText("@" + qualifiedName, this), null);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiModifierList:" + getText();
  }
}

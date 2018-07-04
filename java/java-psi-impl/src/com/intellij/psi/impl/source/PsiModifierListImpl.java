/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakInterner;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.PsiModifier.*;

public class PsiModifierListImpl extends JavaStubPsiElement<PsiModifierListStub> implements PsiModifierList {
  private static final Map<String, IElementType> NAME_TO_KEYWORD_TYPE_MAP;
  private static final Map<IElementType, String> KEYWORD_TYPE_TO_NAME_MAP;
  static {
    NAME_TO_KEYWORD_TYPE_MAP = new THashMap<>();
    NAME_TO_KEYWORD_TYPE_MAP.put(PUBLIC, JavaTokenType.PUBLIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PROTECTED, JavaTokenType.PROTECTED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PRIVATE, JavaTokenType.PRIVATE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(STATIC, JavaTokenType.STATIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(ABSTRACT, JavaTokenType.ABSTRACT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(FINAL, JavaTokenType.FINAL_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(NATIVE, JavaTokenType.NATIVE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(SYNCHRONIZED, JavaTokenType.SYNCHRONIZED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(STRICTFP, JavaTokenType.STRICTFP_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(TRANSIENT, JavaTokenType.TRANSIENT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(VOLATILE, JavaTokenType.VOLATILE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(DEFAULT, JavaTokenType.DEFAULT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(OPEN, JavaTokenType.OPEN_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(TRANSITIVE, JavaTokenType.TRANSITIVE_KEYWORD);

    KEYWORD_TYPE_TO_NAME_MAP = ContainerUtil.newTroveMap();
    for (String name : NAME_TO_KEYWORD_TYPE_MAP.keySet()) {
      KEYWORD_TYPE_TO_NAME_MAP.put(NAME_TO_KEYWORD_TYPE_MAP.get(name), name);
    }
  }

  private volatile ModifierCache myModifierCache;

  public PsiModifierListImpl(final PsiModifierListStub stub) {
    super(stub, JavaStubElementTypes.MODIFIER_LIST);
  }

  public PsiModifierListImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    ModifierCache modifierCache = myModifierCache;
    if (modifierCache == null || !modifierCache.isUpToDate()) {
      myModifierCache = modifierCache = calcModifiers();
    }
    return modifierCache.modifiers.contains(name);
  }

  private ModifierCache calcModifiers() {
    Set<String> modifiers = calcExplicitModifiers();
    modifiers.addAll(calcImplicitModifiers(modifiers));
    if (!modifiers.contains(PUBLIC) && !modifiers.contains(PROTECTED) && !modifiers.contains(PRIVATE)) {
      modifiers.add(PACKAGE_LOCAL);
    }
    PsiFile file = getContainingFile();
    return new ModifierCache(file, PsiAugmentProvider.transformModifierProperties(this, file.getProject(), modifiers));
  }

  private Set<String> calcExplicitModifiers() {
    Set<String> explicitModifiers = ContainerUtil.newHashSet();
    PsiModifierListStub stub = getGreenStub();
    if (stub != null) {
      int mask = stub.getModifiersMask();
      for (int i = 0; i < 31; i++) {
        int flag = 1 << i;
        if (BitUtil.isSet(mask, flag)) {
          ContainerUtil.addIfNotNull(explicitModifiers, ModifierFlags.MODIFIER_FLAG_TO_NAME_MAP.get(flag));
        }
      }
    } else {
      for (ASTNode child : getNode().getChildren(null)) {
        ContainerUtil.addIfNotNull(explicitModifiers, KEYWORD_TYPE_TO_NAME_MAP.get(child.getElementType()));
      }
    }

    return explicitModifiers;
  }

  private Set<String> calcImplicitModifiers(Set<String> explicitModifiers) {
    Set<String> implicitModifiers = ContainerUtil.newHashSet();
    PsiElement parent = getParent();
    if (parent instanceof PsiClass) {
      PsiElement grandParent = parent.getContext();
      if (grandParent instanceof PsiClass && ((PsiClass)grandParent).isInterface()) {
        Collections.addAll(implicitModifiers, PUBLIC, STATIC);
      }
      if (((PsiClass)parent).isInterface()) {
        implicitModifiers.add(ABSTRACT);

        // nested interface is implicitly static
        if (grandParent instanceof PsiClass) {
          implicitModifiers.add(STATIC);
        }
      }
      if (((PsiClass)parent).isEnum()) {
        if (!(grandParent instanceof PsiFile)) {
          implicitModifiers.add(STATIC);
        }
        List<PsiField> fields = parent instanceof PsiExtensibleClass ? ((PsiExtensibleClass)parent).getOwnFields()
                                                                     : Arrays.asList(((PsiClass)parent).getFields());
        boolean hasSubClass = ContainerUtil.find(fields, field -> field instanceof PsiEnumConstant && ((PsiEnumConstant)field).getInitializingClass() != null) != null;
        if (!hasSubClass) {
          implicitModifiers.add(FINAL);
        }

        List<PsiMethod> methods = parent instanceof PsiExtensibleClass ? ((PsiExtensibleClass)parent).getOwnMethods()
                                                                       : Arrays.asList(((PsiClass)parent).getMethods());
        for (PsiMethod method : methods) {
          if (method.hasModifierProperty(ABSTRACT)) {
            implicitModifiers.add(ABSTRACT);
            break;
          }
        }
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiClass aClass = ((PsiMethod)parent).getContainingClass();
      if (aClass != null && aClass.isInterface()) {
        if (!explicitModifiers.contains(PRIVATE)) {
          implicitModifiers.add(PUBLIC);
          if (!explicitModifiers.contains(DEFAULT) && !explicitModifiers.contains(STATIC)) {
            implicitModifiers.add(ABSTRACT);
          }
        }
      }
      else if (aClass != null && aClass.isEnum() && ((PsiMethod)parent).isConstructor()) {
        implicitModifiers.add(PRIVATE);
      }
    }
    else if (parent instanceof PsiField) {
      if (parent instanceof PsiEnumConstant) {
        Collections.addAll(implicitModifiers, PUBLIC, STATIC, FINAL);
      }
      else {
        PsiClass aClass = ((PsiField)parent).getContainingClass();
        if (aClass != null && aClass.isInterface()) {
          Collections.addAll(implicitModifiers, PUBLIC, STATIC, FINAL);
        }
      }
    }
    else if (parent instanceof PsiParameter &&
             parent.getParent() instanceof PsiCatchSection &&
             ((PsiParameter)parent).getType() instanceof PsiDisjunctionType) {
      Collections.addAll(implicitModifiers, FINAL);
    }
    else if (parent instanceof PsiResourceVariable) {
      Collections.addAll(implicitModifiers, FINAL);
    }
    return implicitModifiers;
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    PsiModifierListStub stub = getGreenStub();
    if (stub != null) {
      return BitUtil.isSet(stub.getModifiersMask(), ModifierFlags.NAME_TO_MODIFIER_FLAG_MAP.get(name));
    }

    final CompositeElement tree = (CompositeElement)getNode();
    final IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);
    return type != null && tree.findChildByType(type) != null;
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
          setModifierProperty(PUBLIC, false);
        }
        if (type != JavaTokenType.PRIVATE_KEYWORD) {
          setModifierProperty(PRIVATE, false);
        }
        if (type != JavaTokenType.PROTECTED_KEYWORD) {
          setModifierProperty(PROTECTED, false);
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
    List<PsiAnnotation> filtered = ContainerUtil.findAll(getAnnotations(), annotation -> {
      PsiAnnotation.TargetType target = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
      return target != null && target != PsiAnnotation.TargetType.UNKNOWN;
    });

    return filtered.toArray(PsiAnnotation.EMPTY_ARRAY);
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

  @Override
  public String toString(){
    return "PsiModifierList:" + getText();
  }

  private static class ModifierCache {
    static final WeakInterner<List<String>> ourInterner = new WeakInterner<>();
    final PsiFile file;
    final List<String> modifiers;
    final long modCount;

    ModifierCache(@NotNull PsiFile file, @NotNull Set<String> modifiers) {
      this.file = file;
      List<String> modifierList = ContainerUtil.newArrayList(modifiers);
      Collections.sort(modifierList);
      this.modifiers = ourInterner.intern(modifierList);
      this.modCount = getModCount();
    }

    private long getModCount() {
      return file.getManager().getModificationTracker().getModificationCount() + file.getModificationStamp();
    }

    boolean isUpToDate() {
      return getModCount() == modCount;
    }
  }
}
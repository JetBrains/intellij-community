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
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class PsiModifierListImpl extends JavaStubPsiElement<PsiModifierListStub> implements PsiModifierList {
  private static final Map<String, IElementType> NAME_TO_KEYWORD_TYPE_MAP = new THashMap<String, IElementType>();

  static{
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
  }

  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();

  static{
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PUBLIC, ModifierFlags.PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PROTECTED, ModifierFlags.PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PRIVATE, ModifierFlags.PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PACKAGE_LOCAL, ModifierFlags.PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STATIC, ModifierFlags.STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.ABSTRACT, ModifierFlags.ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.FINAL, ModifierFlags.FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.NATIVE, ModifierFlags.NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.SYNCHRONIZED, ModifierFlags.SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STRICTFP, ModifierFlags.STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.TRANSIENT, ModifierFlags.TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.VOLATILE, ModifierFlags.VOLATILE_MASK);
  }

  public PsiModifierListImpl(final PsiModifierListStub stub) {
    super(stub, JavaStubElementTypes.MODIFIER_LIST);
  }

  public PsiModifierListImpl(final ASTNode node) {
    super(node);
  }

  public boolean hasModifierProperty(@NotNull String name){
    final PsiModifierListStub stub = getStub();
    if (stub != null) {
      int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
      assert flag != 0;
      return (stub.getModifiersMask() & flag) != 0;
    }

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    PsiElement parent = getParent();
    if (parent instanceof PsiClass){
      PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass && ((PsiClass)pparent).isInterface()){
        if (type == JavaTokenType.PUBLIC_KEYWORD){
          return true;
        }
        if (type == null){ // package local
          return false;
        }
        if (type == JavaTokenType.STATIC_KEYWORD){
          return true;
        }
      }
      if (((PsiClass)parent).isInterface()){
        if (type == JavaTokenType.ABSTRACT_KEYWORD){
          return true;
        }

        // nested interface is implicitly static
        if (pparent instanceof PsiClass) {
          if (type == JavaTokenType.STATIC_KEYWORD){
            return true;
          }
        }
      }
      if (((PsiClass)parent).isEnum()){
        if (type == JavaTokenType.STATIC_KEYWORD) {
          if (!(pparent instanceof PsiFile)) return true;
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
    else if (parent instanceof PsiMethod){
      PsiClass aClass = ((PsiMethod)parent).getContainingClass();
      if (aClass != null && aClass.isInterface()){
        if (type == JavaTokenType.PUBLIC_KEYWORD){
          return true;
        }
        if (type == null){ // package local
          return false;
        }
        if (type == JavaTokenType.ABSTRACT_KEYWORD){
          return true;
        }
      }
    }
    else if (parent instanceof PsiField){
      if (parent instanceof PsiEnumConstant) {
        return type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.STATIC_KEYWORD || type == JavaTokenType.FINAL_KEYWORD;
      }
      else {
        PsiClass aClass = ((PsiField)parent).getContainingClass();
        if (aClass != null && aClass.isInterface()){
          if (type == JavaTokenType.PUBLIC_KEYWORD){
            return true;
          }
          if (type == null){ // package local
            return false;
          }
          if (type == JavaTokenType.STATIC_KEYWORD){
            return true;
          }
          if (type == JavaTokenType.FINAL_KEYWORD){
            return true;
          }
        }
      }
    }

    if (type == null){ // package local
      return !hasModifierProperty(PsiModifier.PUBLIC) && !hasModifierProperty(PsiModifier.PRIVATE) && !hasModifierProperty(PsiModifier.PROTECTED);
    }

    return getNode().findChildByType(type) != null;
  }

  public boolean hasExplicitModifier(@NotNull String name) {
    final CompositeElement tree = (CompositeElement)getNode();
    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);
    return tree.findChildByType(type) != null;
  }

  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    checkSetModifierProperty(name, value);

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    CompositeElement treeElement = (CompositeElement)getNode();
    ASTNode parentTreeElement = treeElement.getTreeParent();
    if (value){
      if (parentTreeElement.getElementType() == JavaElementType.FIELD &&
          parentTreeElement.getTreeParent().getElementType() == JavaElementType.CLASS &&
          ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.STATIC_KEYWORD || type == JavaTokenType.FINAL_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == JavaElementType.METHOD &&
               parentTreeElement.getTreeParent().getElementType() == JavaElementType.CLASS &&
               ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.ABSTRACT_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == JavaElementType.CLASS &&
               parentTreeElement.getTreeParent().getElementType() == JavaElementType.CLASS &&
               ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == JavaElementType.ANNOTATION_METHOD &&
               parentTreeElement.getTreeParent().getElementType() == JavaElementType.CLASS &&
               ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isAnnotationType()) {
        if (type == JavaTokenType.PUBLIC_KEYWORD || type == JavaTokenType.ABSTRACT_KEYWORD) return;
      }

      if (type == JavaTokenType.PUBLIC_KEYWORD
          || type == JavaTokenType.PRIVATE_KEYWORD
          || type == JavaTokenType.PROTECTED_KEYWORD
          || type == null /* package local */){

        if (type != JavaTokenType.PUBLIC_KEYWORD){
          setModifierProperty(PsiModifier.PUBLIC, false);
        }
        if (type != JavaTokenType.PRIVATE_KEYWORD){
          setModifierProperty(PsiModifier.PRIVATE, false);
        }
        if (type != JavaTokenType.PROTECTED_KEYWORD){
          setModifierProperty(PsiModifier.PROTECTED, false);
        }
        if (type == null) return;
      }

      if (treeElement.findChildByType(type) == null){
        TreeElement keyword = Factory.createSingleLeafElement(type, name, null, getManager());
        treeElement.addInternal(keyword, keyword, null, null);
      }
      if ((type == JavaTokenType.ABSTRACT_KEYWORD || type == JavaTokenType.NATIVE_KEYWORD) && parentTreeElement.getElementType() ==
                                                                                              JavaElementType.METHOD){
        //Q: remove body?
      }
    }
    else{
      if (type == null){ // package local
        throw new IncorrectOperationException("Cannot reset package local modifier."); //?
      }
      ASTNode child = treeElement.findChildByType(type);
      if (child != null){
        SourceTreeToPsiMap.treeElementToPsi(child).delete();
      }
    }
  }

  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException{
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    final PsiAnnotation[] owns = getStubOrPsiChildren(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
    final List<PsiAnnotation> augments = PsiAugmentProvider.collectAugments(this, PsiAnnotation.class);
    return ArrayUtil.mergeArrayAndCollection(owns, augments, PsiAnnotation.ARRAY_FACTORY);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    final String[] fields = AnnotationsHighlightUtil.getApplicableElementTypeFields(this);
    List<PsiAnnotation> filtered = ContainerUtil.findAll(getAnnotations(), new Condition<PsiAnnotation>() {
      public boolean value(PsiAnnotation annotation) {
        return AnnotationsHighlightUtil.isAnnotationApplicableTo(annotation, true, fields);
      }
    });

    return filtered.toArray(new PsiAnnotation[filtered.size()]);
  }

  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return (PsiAnnotation)addAfter(JavaPsiFacade.getInstance(getProject()).getElementFactory().createAnnotationFromText("@" + qualifiedName, this), null);
  }

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

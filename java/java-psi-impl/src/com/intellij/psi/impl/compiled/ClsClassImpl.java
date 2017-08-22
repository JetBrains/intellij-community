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
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static java.util.Arrays.asList;

public class ClsClassImpl extends ClsMemberImpl<PsiClassStub<?>> implements PsiExtensibleClass, Queryable {
  public static final Key<PsiClass> DELEGATE_KEY = Key.create("DELEGATE");

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

  public ClsClassImpl(final PsiClassStub stub) {
    super(stub);
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    List<PsiElement> children = ContainerUtil.newArrayList();
    ContainerUtil.addAll(children, getChildren(getDocComment(), getModifierList(), getNameIdentifier(), getExtendsList(), getImplementsList()));
    ContainerUtil.addAll(children, getOwnFields());
    ContainerUtil.addAll(children, getOwnMethods());
    ContainerUtil.addAll(children, getOwnInnerClasses());
    return PsiUtilCore.toPsiElementArray(children);
  }

  @Override
  @NotNull
  public PsiTypeParameterList getTypeParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nullable
  public String getQualifiedName() {
    return getStub().getQualifiedName();
  }
  
  boolean isAnonymousOrLocalClass() {
    PsiClassStub<?> stub = getStub();
    return !(stub instanceof PsiClassStubImpl) || 
           ((PsiClassStubImpl)stub).isAnonymousInner() || 
           ((PsiClassStubImpl)stub).isLocalClassInner();
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiReferenceList getExtendsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST).getPsi();
  }

  @Override
  @NotNull
  public PsiReferenceList getImplementsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST).getPsi();
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @NotNull
  @Override
  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
      return PsiClass.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
      return PsiClassType.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @NotNull
  @Override
  public List<PsiField> getOwnFields() {
    return asList(getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return asList(getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    PsiClass[] classes = getStub().getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
    if (classes.length == 0) return Collections.emptyList();
    
    int anonymousOrLocalClassesCount = 0;
    for(PsiClass aClass:classes) {
      if (aClass instanceof ClsClassImpl && ((ClsClassImpl)aClass).isAnonymousOrLocalClass()) {
        ++anonymousOrLocalClassesCount;
      }
    }
    if (anonymousOrLocalClassesCount == 0) return asList(classes);
    
    ArrayList<PsiClass> result = new ArrayList<>(classes.length - anonymousOrLocalClassesCount);
    for(PsiClass aClass:classes) {
      if (!(aClass instanceof ClsClassImpl) || !((ClsClassImpl)aClass).isAnonymousOrLocalClass()) {
        result.add(aClass);
      }
    }
    return result;
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return myInnersCache.findInnerClassByName(name, checkBases);
  }

  @Override
  public boolean isDeprecated() {
    return getStub().isDeprecated() || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public String getSourceFileName() {
    final String sfn = getStub().getSourceFileName();
    return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
  }

  @NonNls
  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
    int i = name.indexOf('$');
    if (i < 0) {
      i = name.indexOf('.');
      if (i < 0) {
        i = name.length();
      }
    }
    return name.substring(0, i) + ".java";
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return getStub().isInterface();
  }

  @Override
  public boolean isAnnotationType() {
    return getStub().isAnnotationType();
  }

  @Override
  public boolean isEnum() {
    return getStub().isEnum();
  }

  @Override
  public void appendMirrorText(final int indentLevel, @NotNull @NonNls final StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);

    appendText(getModifierList(), indentLevel, buffer);
    buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
    appendText(getNameIdentifier(), indentLevel, buffer, " ");
    appendText(getTypeParameterList(), indentLevel, buffer, " ");
    appendText(getExtendsList(), indentLevel, buffer, " ");
    appendText(getImplementsList(), indentLevel, buffer, " ");

    buffer.append('{');

    int newIndentLevel = indentLevel + getIndentSize();
    List<PsiField> fields = getOwnFields();
    List<PsiMethod> methods = getOwnMethods();
    List<PsiClass> classes = getOwnInnerClasses();

    if (!fields.isEmpty()) {
      goNextLine(newIndentLevel, buffer);

      for (int i = 0; i < fields.size(); i++) {
        PsiField field = fields.get(i);
        appendText(field, newIndentLevel, buffer);

        if (field instanceof ClsEnumConstantImpl) {
          if (i < fields.size() - 1 && fields.get(i + 1) instanceof ClsEnumConstantImpl) {
            buffer.append(", ");
          }
          else {
            buffer.append(';');
            if (i < fields.size() - 1) {
              buffer.append('\n');
              goNextLine(newIndentLevel, buffer);
            }
          }
        }
        else if (i < fields.size() - 1) {
          goNextLine(newIndentLevel, buffer);
        }
      }
    }
    else if (isEnum() && methods.size() + classes.size() > 0) {
      goNextLine(newIndentLevel, buffer);
      buffer.append(";");
    }

    if (!methods.isEmpty()) {
      if (isEnum() || !fields.isEmpty()) {
        buffer.append('\n');
      }
      goNextLine(newIndentLevel, buffer);

      for (int i = 0; i < methods.size(); i++) {
        appendText(methods.get(i), newIndentLevel, buffer);

        if (i < methods.size() - 1) {
          buffer.append('\n');
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    if (!classes.isEmpty()) {
      if (fields.size() + methods.size() > 0) {
        buffer.append('\n');
      }
      goNextLine(newIndentLevel, buffer);

      for (int i = 0; i < classes.size(); i++) {
        appendText(classes.get(i), newIndentLevel, buffer);

        if (i < classes.size() - 1) {
          buffer.append('\n');
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    goNextLine(indentLevel, buffer);
    buffer.append('}');
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiClass mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirrorIfPresent(getDocComment(), mirror.getDocComment());

    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
    setMirror(getTypeParameterList(), mirror.getTypeParameterList());
    setMirror(getExtendsList(), mirror.getExtendsList());
    setMirror(getImplementsList(), mirror.getImplementsList());

    if (mirror instanceof PsiExtensibleClass) {
      PsiExtensibleClass extMirror = (PsiExtensibleClass)mirror;
      setMirrors(getOwnFields(), extMirror.getOwnFields());
      setMirrors(getOwnMethods(), extMirror.getOwnMethods());
      setMirrors(getOwnInnerClasses(), extMirror.getOwnInnerClasses());
    }
    else {
      setMirrors(getOwnFields(), asList(mirror.getFields()));
      setMirrors(getOwnMethods(), asList(mirror.getMethods()));
      setMirrors(getOwnInnerClasses(), asList(mirror.getInnerClasses()));
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (isEnum()) {
      if (!PsiClassImplUtil.processDeclarationsInEnum(processor, state, myInnersCache)) return false;
    }

    LanguageLevel level = processor instanceof MethodsProcessor ? ((MethodsProcessor)processor).getLanguageLevel() : PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @Override
  public PsiElement getScope() {
    return getParent();
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Nullable
  public PsiClass getSourceMirrorClass() {
    final PsiClass delegate = getUserData(DELEGATE_KEY);
    if (delegate instanceof ClsClassImpl) {
      return ((ClsClassImpl)delegate).getSourceMirrorClass();
    }

    final String name = getName();
    final PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      if (!(parent instanceof PsiClassOwner)) return null;

      PsiClassOwner fileNavigationElement = (PsiClassOwner)parent.getNavigationElement();
      if (fileNavigationElement == parent) return null;

      for (PsiClass aClass : fileNavigationElement.getClasses()) {
        if (name.equals(aClass.getName())) return aClass;
      }
    }
    else if (parent != null) {
      ClsClassImpl parentClass = (ClsClassImpl)parent;
      PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
      if (parentSourceMirror == null) return null;
      PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (name.equals(innerClass.getName())) return innerClass;
      }
    }
    else {
      throw new PsiInvalidElementAccessException(this);
    }

    return null;
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      try {
        PsiElement navigationElement = customNavigationPolicy.getNavigationElement(this);
        if (navigationElement != null) {
          return navigationElement;
        }
      }
      catch (IndexNotReadyException ignored) { }
    }

    try {
      PsiClass aClass = getSourceMirrorClass();

      if (aClass != null) {
        return aClass.getNavigationElement();
      }

      if ("package-info".equals(getName())) {
        PsiElement parent = getParent();
        if (parent instanceof ClsFileImpl) {
          PsiElement sourceFile = parent.getNavigationElement();
          if (sourceFile instanceof PsiJavaFile) {
            return sourceFile;
          }
        }
      }
    }
    catch (IndexNotReadyException ignore) { }

    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    PsiClassImpl.putInfo(this, info);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}
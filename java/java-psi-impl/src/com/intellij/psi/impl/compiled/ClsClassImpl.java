// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
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
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiRecordHeaderStub;
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
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class ClsClassImpl extends ClsMemberImpl<PsiClassStub<?>> implements PsiExtensibleClass, Queryable {
  private static final Key<PsiClass> DELEGATE_KEY = Key.create("DELEGATE");

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

  public ClsClassImpl(final PsiClassStub<?> stub) {
    super(stub);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    List<PsiElement> children = new ArrayList<>();
    ContainerUtil.addAll(children, getChildren(getDocComment(), getModifierListInternal(), getNameIdentifier(),
                                               getExtendsList(), getImplementsList(), getPermitsList()));
    children.addAll(getOwnFields());
    children.addAll(getOwnMethods());
    children.addAll(getOwnInnerClasses());
    return PsiUtilCore.toPsiElementArray(children);
  }

  @Override
  public @NotNull PsiTypeParameterList getTypeParameterList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST)).getPsi();
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public @Nullable String getQualifiedName() {
    return getStub().getQualifiedName();
  }

  private boolean isLocalClass() {
    PsiClassStub<?> stub = getStub();
    return stub instanceof PsiClassStubImpl &&
           ((PsiClassStubImpl<?>)stub).isLocalClassInner();
  }

  private boolean isAnonymousOrLocalClass() {
    return this instanceof PsiAnonymousClass || isLocalClass();
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return getModifierListInternal();
  }

  private PsiModifierList getModifierListInternal() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST)).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierListInternal().hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiReferenceList getExtendsList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST)).getPsi();
  }

  @Override
  public @Nullable PsiReferenceList getPermitsList() {
    PsiClassReferenceListStub type = getStub().findChildStubByType(JavaStubElementTypes.PERMITS_LIST);
    return type == null ? null : type.getPsi();
  }

  @Override
  public @NotNull PsiReferenceList getImplementsList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST)).getPsi();
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
      return PsiClass.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
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
  public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @Override
  public @NotNull List<PsiField> getOwnFields() {
    return asList(getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @Override
  public @NotNull List<PsiMethod> getOwnMethods() {
    return asList(getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @Override
  public @NotNull List<PsiClass> getOwnInnerClasses() {
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
  public PsiRecordComponent @NotNull [] getRecordComponents() {
    PsiRecordHeader header = getRecordHeader();
    return header == null ? PsiRecordComponent.EMPTY_ARRAY : header.getRecordComponents();
  }

  @Override
  public @Nullable PsiRecordHeader getRecordHeader() {
    PsiRecordHeaderStub headerStub = getStub().findChildStubByType(JavaStubElementTypes.RECORD_HEADER);
    return headerStub == null ? null : headerStub.getPsi();
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
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

  private @NonNls String obtainSourceFileNameFromClassFileName() {
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
  public boolean isRecord() {
    return getStub().isRecord();
  }

  @Override
  public void appendMirrorText(final int indentLevel, final @NotNull @NonNls StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);

    appendText(getModifierListInternal(), indentLevel, buffer);
    buffer.append(isEnum() ? "enum " : 
                  isAnnotationType() ? "@interface " : 
                  isInterface() ? "interface " :
                  isRecord() ? "record " :
                  "class ");
    PsiRecordHeader header = getRecordHeader();
    if (header != null) {
      appendText(getNameIdentifier(), indentLevel, buffer, "");
      appendText(header, indentLevel, buffer, " ");
    } else {
      appendText(getNameIdentifier(), indentLevel, buffer, " ");
    }
    appendText(getTypeParameterList(), indentLevel, buffer, " ");
    appendText(getExtendsList(), indentLevel, buffer, " ");
    appendText(getImplementsList(), indentLevel, buffer, " ");
    appendText(getPermitsList(), indentLevel, buffer, " ");

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
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiClass mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirrorIfPresent(getDocComment(), mirror.getDocComment());

    PsiModifierList modifierList = getModifierList();
    if (modifierList != null && mirror.getModifierList() != null) setMirror(modifierList, mirror.getModifierList());
    if (mirror.getNameIdentifier() != null) setMirrorChecked(getNameIdentifier(), mirror.getNameIdentifier());
    if (mirror.getTypeParameterList() != null) setMirrorChecked(getTypeParameterList(), mirror.getTypeParameterList());
    if (mirror.getExtendsList() != null) setMirrorChecked(getExtendsList(), mirror.getExtendsList());
    if (mirror.getImplementsList() != null) setMirrorChecked(getImplementsList(), mirror.getImplementsList());

    if (mirror instanceof PsiExtensibleClass) {
      PsiExtensibleClass extMirror = (PsiExtensibleClass)mirror;
      setMirrorsChecked(getOwnFields(), extMirror.getOwnFields());
      setMirrorsChecked(getOwnMethods(), extMirror.getOwnMethods());
      //inner classes are sorted by decompiler by method lines, so it is necessary to resort
      setSortedMirrorsChecked(getOwnInnerClasses(), extMirror.getOwnInnerClasses(), Comparator.comparing(PsiClass::getName));
    }
    else {
      setMirrorsChecked(getOwnFields(), asList(mirror.getFields()));
      setMirrorsChecked(getOwnMethods(), asList(mirror.getMethods()));
      //inner classes are sorted by decompiler by method lines, so it is necessary to resort
      setSortedMirrorsChecked(getOwnInnerClasses(), asList(mirror.getInnerClasses()), Comparator.comparing(PsiClass::getName));
    }
  }

  private static <T extends  PsiElement> void setMirrorChecked(@NotNull T stub, @NotNull T mirror) {
    setMirror(stub, mirror);
  }

  private static <T extends  PsiElement> void setMirrorsChecked(@NotNull List<T> stub, @NotNull List<T> mirror) {
    if (stub.size() == mirror.size()) {
      setMirrors(stub, mirror);
    }
  }

  private static <T extends PsiElement> void setSortedMirrorsChecked(@NotNull List<T> stub,
                                                                     @NotNull List<T> mirror,
                                                                     @NotNull Comparator<? super T> comparator) {
    setMirrorsChecked(stub.stream().sorted(comparator).collect(Collectors.toList()),
                      mirror.stream().sorted(comparator).collect(Collectors.toList()));
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

  @Override
  public @NonNls String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    LanguageLevel level = processor instanceof MethodsProcessor ? ((MethodsProcessor)processor).getLanguageLevel() : PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @Override
  public PsiElement getScope() {
    return getParent();
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public @Nullable PsiClass getSourceMirrorClass() {
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
  public @NotNull PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy navigationPolicy : ClsCustomNavigationPolicy.EP_NAME.getExtensionList()) {
      try {
        PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
        if (navigationElement != null) return navigationElement;
      }
      catch (IndexNotReadyException ignored) { }
    }

    try {
      PsiClass aClass = getSourceMirrorClass();
      if (aClass != null) return aClass.getNavigationElement();

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
  protected @Nullable Icon getBaseIcon() {
    return PsiClassImplUtil.getClassIcon(0, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    PsiClassImpl.putInfo(this, info);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}

package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class LightClass extends LightElement implements PsiClass {
  private final PsiClass myDelegate;

  public LightClass(PsiClass delegate) {
    this(delegate, StdLanguages.JAVA);
  }

  public LightClass(PsiClass delegate, final Language language) {
    super(delegate.getManager(), language);
    myDelegate = delegate;
  }

  @NonNls
  @Nullable
  public String getName() {
    return myDelegate.getName();
  }

  @Nullable
  public PsiModifierList getModifierList() {
    return myDelegate.getModifierList();
  }

  public boolean hasModifierProperty(@Modifier @NonNls @NotNull String name) {
    return myDelegate.hasModifierProperty(name);
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return myDelegate.isDeprecated();
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return myDelegate.getTypeParameterList();
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myDelegate.getTypeParameters();
  }

  @NonNls
  @Nullable
  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  public boolean isInterface() {
    return myDelegate.isInterface();
  }

  public boolean isAnnotationType() {
    return myDelegate.isAnnotationType();
  }

  public boolean isEnum() {
    return myDelegate.isEnum();
  }

  @Nullable
  public PsiReferenceList getExtendsList() {
    return myDelegate.getExtendsList();
  }

  @Nullable
  public PsiReferenceList getImplementsList() {
    return myDelegate.getImplementsList();
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Nullable
  public PsiClass getSuperClass() {
    return myDelegate.getSuperClass();
  }

  public PsiClass[] getInterfaces() {
    return myDelegate.getInterfaces();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myDelegate.getNavigationElement();
  }

  @NotNull
  public PsiClass[] getSupers() {
    return myDelegate.getSupers();
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return myDelegate.getSuperTypes();
  }

  @NotNull
  public PsiField[] getFields() {
    return myDelegate.getFields();
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return myDelegate.getMethods();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return myDelegate.getConstructors();
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return myDelegate.getInnerClasses();
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return myDelegate.getInitializers();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false);
  }

  @NotNull
  public PsiField[] getAllFields() {
    return myDelegate.getAllFields();
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return myDelegate.getAllMethods();
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return myDelegate.getAllInnerClasses();
  }

  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return myDelegate.findInnerClassByName(name, checkBases);
  }

  @Nullable
  public PsiJavaToken getLBrace() {
    return myDelegate.getLBrace();
  }

  @Nullable
  public PsiJavaToken getRBrace() {
    return myDelegate.getRBrace();
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return myDelegate.getNameIdentifier();
  }

  public PsiElement getScope() {
    return myDelegate.getScope();
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return myDelegate.isInheritor(baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return myDelegate.isInheritorDeep(baseClass, classToByPass);
  }

  @Nullable
  public PsiClass getContainingClass() {
    return myDelegate.getContainingClass();
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return myDelegate.getVisibleSignatures();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return myDelegate.setName(name);
  }

  @Override
  public String toString() {
    return "PsiClass:" + getName();
  }

  public String getText() {
    return myDelegate.getText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public PsiElement copy() {
    return new LightClass(this);
  }

  @Override
  public PsiFile getContainingFile() {
    return myDelegate.getContainingFile();
  }

  public PsiClass getDelegate() {
    return myDelegate;
  }

  @Override
  public PsiElement getContext() {
    return myDelegate;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return this == another || getDelegate().isEquivalentTo(another);
  }
}

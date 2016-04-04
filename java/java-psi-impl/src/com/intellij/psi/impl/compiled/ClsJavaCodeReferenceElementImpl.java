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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ClsJavaCodeReferenceElementImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement {
  private final PsiElement myParent;
  private final String myCanonicalText;
  private final String myQualifiedName;
  private final PsiReferenceParameterList myRefParameterList;

  public ClsJavaCodeReferenceElementImpl(PsiElement parent, @NotNull String canonicalText) {
    myParent = parent;

    String canonical = TypeInfo.internFrequentType(canonicalText);
    myCanonicalText = canonical;
    String qName = TypeInfo.internFrequentType(PsiNameHelper.getQualifiedClassName(myCanonicalText, false));
    myQualifiedName = qName.equals(canonical) ? canonical : qName;

    String[] classParameters = PsiNameHelper.getClassParametersText(canonicalText);
    myRefParameterList = classParameters.length == 0 ? null : new ClsReferenceParameterListImpl(this, classParameters);
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public String getText() {
    return PsiNameHelper.getPresentableText(this);
  }

  @Override
  public int getTextLength() {
    return getText().length();
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myCanonicalText;
  }

  private static class Resolver implements ResolveCache.PolyVariantContextResolver<ClsJavaCodeReferenceElementImpl> {
    public static final Resolver INSTANCE = new Resolver();

    @NotNull
    @Override
    public JavaResolveResult[] resolve(@NotNull ClsJavaCodeReferenceElementImpl ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      final JavaResolveResult resolveResult = ref.advancedResolveImpl(containingFile);
      return resolveResult == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[] {resolveResult};
    }
  }

  private JavaResolveResult advancedResolveImpl(@NotNull PsiFile containingFile) {
    PsiTypeElement[] typeElements = myRefParameterList == null ? PsiTypeElement.EMPTY_ARRAY : myRefParameterList.getTypeParameterElements();
    PsiElement resolve = resolveElement(containingFile);
    if (resolve == null) return null;
    if (resolve instanceof PsiClass) {
      Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
      int index = 0;
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable((PsiClass)resolve)) {
        if (index >= typeElements.length) {
          PsiTypeParameterListOwner parameterOwner = parameter.getOwner();
          if (parameterOwner == resolve) {
            substitutionMap.put(parameter, null);
          }
          else if (parameterOwner instanceof PsiClass) {
            PsiElement containingClass = myParent;
            while ((containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true)) != null) {
              PsiSubstitutor superClassSubstitutor =
                TypeConversionUtil.getClassSubstitutor((PsiClass)parameterOwner, (PsiClass)containingClass, PsiSubstitutor.EMPTY);
              if (superClassSubstitutor != null) {
                substitutionMap.put(parameter, superClassSubstitutor.substitute(parameter));
                break;
              }
            }
          }
        }
        else {
          substitutionMap.put(parameter, typeElements[index].getType());
        }
        index++;
      }
      collectOuterClassTypeArgs((PsiClass)resolve, myCanonicalText, substitutionMap);
      return new CandidateInfo(resolve, PsiSubstitutorImpl.createSubstitutor(substitutionMap));
    }
    else {
      return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
    }
  }

  private void collectOuterClassTypeArgs(@NotNull PsiClass psiClass,
                                         final String canonicalText,
                                         final Map<PsiTypeParameter, PsiType> substitutionMap) {
    final PsiClass containingClass = psiClass.getContainingClass();
    if (containingClass != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      final String outerClassRef = getOuterClassRef(canonicalText);
      final String[] classParameters = PsiNameHelper.getClassParametersText(outerClassRef);
      final PsiType[] args = classParameters.length == 0 ? null : new ClsReferenceParameterListImpl(this, classParameters).getTypeArguments();
      final PsiTypeParameter[] typeParameters = containingClass.getTypeParameters();
      for (int i = 0; i < typeParameters.length; i++) {
        if (args != null && i < args.length) {
          substitutionMap.put(typeParameters[i], args[i]);
        }
      }
      collectOuterClassTypeArgs(containingClass, outerClassRef, substitutionMap);
    }
  }

  @NotNull
  @Contract(pure = true)
  private static String getOuterClassRef(String ref) {
    int stack = 0;
    for (int i = ref.length() - 1; i >= 0; i--) {
      char c = ref.charAt(i);
      switch (c) {
        case '<':
          stack--;
          break;
        case '>':
          stack++;
          break;
        case '.':
          if (stack == 0) return ref.substring(0, i);
      }
    }

    return "";
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    final ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    ResolveResult[] results = resolveCache.resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode,file);
    if (results.length == 0) return JavaResolveResult.EMPTY_ARRAY;
    return (JavaResolveResult[])results;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Nullable
  private PsiElement resolveElement(@NotNull PsiFile containingFile) {
    PsiElement element = getParent();
    while(element != null && (!(element instanceof PsiClass) || element instanceof PsiTypeParameter)) {
      if(element instanceof PsiMethod){
        final PsiMethod method = (PsiMethod)element;
        final PsiTypeParameterList list = method.getTypeParameterList();
        if (list != null) {
          final PsiTypeParameter[] parameters = list.getTypeParameters();
          for (int i = 0; parameters != null && i < parameters.length; i++) {
            final PsiTypeParameter parameter = parameters[i];
            if (myQualifiedName.equals(parameter.getName())) return parameter;
          }
        }
      }
      element = element.getParent();
    }
    if (element == null) return null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable((PsiTypeParameterListOwner)element)) {
      if (myQualifiedName.equals(parameter.getName())) return parameter;
    }

    Project project = containingFile.getProject();
    GlobalSearchScope scope = ResolveScopeManager.getInstance(project).getResolveScope(this);
    return JavaPsiFacade.getInstance(project).findClass(myQualifiedName, scope);
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return myRefParameterList;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  @Override
  public String getReferenceName() {
    return PsiNameHelper.getShortClassName(myCanonicalText);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myCanonicalText.equals(aClass.getQualifiedName()) || getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void appendMirrorText(final int indentLevel, @NotNull final StringBuilder buffer) {
    buffer.append(getCanonicalText());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.JAVA_CODE_REFERENCE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  @NotNull
  public PsiType[] getTypeParameters() {
    return myRefParameterList == null ? PsiType.EMPTY_ARRAY : myRefParameterList.getTypeArguments();
  }

  @Override
  public boolean isQualified() {
    return false;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }
}

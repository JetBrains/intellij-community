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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ClsJavaCodeReferenceElementImpl extends ClsElementImpl implements PsiAnnotatedJavaCodeReferenceElement {
  private final PsiElement myParent;
  private final String myCanonicalText;
  private String myShortName;
  private final String myQualifiedName;
  private final PsiReferenceParameterList myRefParameterList;
  private final TypeAnnotationContainer myAnnotations;
  private final ClsJavaCodeReferenceElementImpl myQualifier;

  public ClsJavaCodeReferenceElementImpl(@NotNull PsiElement parent, @NotNull String canonicalText) {
    this(parent, canonicalText, TypeAnnotationContainer.EMPTY);
  }
  
  public ClsJavaCodeReferenceElementImpl(@NotNull PsiElement parent,
                                         @NotNull String canonicalText,
                                         @NotNull TypeAnnotationContainer annotations) {
    myParent = parent;

    String canonical = TypeInfo.internFrequentType(canonicalText);
    myCanonicalText = canonical;
    String qName = TypeInfo.internFrequentType(PsiNameHelper.getQualifiedClassName(myCanonicalText, false));
    myQualifiedName = qName.equals(canonical) ? canonical : qName;

    String[] classParameters = PsiNameHelper.getClassParametersText(canonicalText);
    myRefParameterList = classParameters.length == 0 ? null : new ClsReferenceParameterListImpl(this, classParameters, annotations);
    myAnnotations = annotations;
    String prefix = PsiNameHelper.getOuterClassReference(canonicalText);
    TypeAnnotationContainer container = prefix.isEmpty() ? TypeAnnotationContainer.EMPTY : annotations.forEnclosingClass();
    myQualifier = container.isEmpty() ? null : new ClsJavaCodeReferenceElementImpl(this, prefix, container);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    if (myQualifier != null) {
      return myRefParameterList != null ? new PsiElement[] {myQualifier, myRefParameterList} : new PsiElement[] {myQualifier};
    }
    return myRefParameterList != null ? new PsiElement[] {myRefParameterList} : PsiElement.EMPTY_ARRAY;
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

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated, PsiAnnotation @Nullable [] annotations) {
    String text = getCanonicalText();
    if (!annotated || annotations == null) return text;

    StringBuilder sb = new StringBuilder();

    String prefix = PsiNameHelper.getOuterClassReference(text);
    int simpleNamePos = 0;
    if (!StringUtil.isEmpty(prefix)) {
      if (myQualifier != null) {
        sb.append(myQualifier.getCanonicalText(true, myQualifier.myAnnotations.getProvider(myQualifier).getAnnotations()));
      } else {
        sb.append(prefix);
      }
      sb.append('.');
      simpleNamePos = prefix.length() + 1;
    }

    PsiNameHelper.appendAnnotations(sb, Arrays.asList(annotations), true);

    int typeArgPos = text.indexOf('<', simpleNamePos);
    if (typeArgPos == -1) {
      sb.append(text, simpleNamePos, text.length());
    } else {
      sb.append(text, simpleNamePos, typeArgPos);
      PsiNameHelper.appendTypeArgs(sb, getTypeParameters(), true, true);
    }

    return sb.toString();
  }

  private static class Resolver implements ResolveCache.PolyVariantContextResolver<ClsJavaCodeReferenceElementImpl> {
    public static final Resolver INSTANCE = new Resolver();

    @Override
    public JavaResolveResult @NotNull [] resolve(@NotNull ClsJavaCodeReferenceElementImpl ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      final JavaResolveResult resolveResult = ref.advancedResolveImpl(containingFile);
      return resolveResult == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[] {resolveResult};
    }
  }

  private JavaResolveResult advancedResolveImpl(@NotNull PsiFile containingFile) {
    PsiTypeElement[] typeElements = myRefParameterList == null ? PsiTypeElement.EMPTY_ARRAY : myRefParameterList.getTypeParameterElements();
    PsiElement resolve = resolveElement(containingFile);
    if (resolve == null) return null;
    if (resolve instanceof PsiClass) {
      Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<>();
      int index = 0;
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable((PsiClass)resolve)) {
        if (index >= typeElements.length) {
          substitutionMap.put(parameter, null);
        }
        else {
          substitutionMap.put(parameter, typeElements[index].getType());
        }
        index++;
      }
      collectOuterClassTypeArgs((PsiClass)resolve, myCanonicalText, substitutionMap);
      return new CandidateInfo(resolve, PsiSubstitutor.createSubstitutor(substitutionMap));
    }
    else {
      return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
    }
  }

  private void collectOuterClassTypeArgs(@NotNull PsiClass psiClass,
                                         final String canonicalText,
                                         final Map<PsiTypeParameter, PsiType> substitutionMap) {
    final PsiClass containingClass = psiClass.getContainingClass();
    if (containingClass != null) {
      final String outerClassRef = PsiNameHelper.getOuterClassReference(canonicalText);
      final String[] classParameters = PsiNameHelper.getClassParametersText(outerClassRef);
      final PsiType[] args = classParameters.length == 0 ? null : 
                             new ClsReferenceParameterListImpl(this, classParameters, TypeAnnotationContainer.EMPTY).getTypeArguments();
      final PsiTypeParameter[] typeParameters = containingClass.getTypeParameters();
      for (int i = 0; i < typeParameters.length; i++) {
        if (args != null) {
          if (i < args.length) {
            substitutionMap.put(typeParameters[i], args[i]);
          }
        }
        else {
          substitutionMap.put(typeParameters[i], null);
        }
      }
      if (!containingClass.hasModifierProperty(PsiModifier.STATIC)) {
        collectOuterClassTypeArgs(containingClass, outerClassRef, substitutionMap);
      }
    }
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    if (file == null) {
      return diagnoseNoFile();
    }
    final ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    ResolveResult[] results = resolveCache.resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode,file);
    if (results.length == 0) return JavaResolveResult.EMPTY_ARRAY;
    return (JavaResolveResult[])results;
  }

  private JavaResolveResult @NotNull [] diagnoseNoFile() {
    PsiElement root = SyntaxTraverser.psiApi().parents(this).last();
    PsiUtilCore.ensureValid(Objects.requireNonNull(root));
    throw new PsiInvalidElementAccessException(this, "parent=" + myParent + ", root=" + root + ", canonicalText=" + myCanonicalText);
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Nullable
  private PsiElement resolveElement(@NotNull PsiFile containingFile) {
    PsiElement element = getParent();
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiMethod) {
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable((PsiMethod)element)) {
          if (myQualifiedName.equals(parameter.getName())) return parameter;
        }
      }
      else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        PsiClass psiClass = (PsiClass)element;
        if (myQualifiedName.equals(psiClass.getQualifiedName())) return element;
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
          if (myQualifiedName.equals(parameter.getName())) return parameter;
        }
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
          if (myQualifiedName.equals(innerClass.getQualifiedName())) return innerClass;
        }
      }
      element = element.getParent();
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
    String name = myShortName;
    if (name == null) {
      name = PsiNameHelper.getShortClassName(myCanonicalText);
      myShortName = name;
    }
    return name;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myCanonicalText.equals(aClass.getQualifiedName()) || getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public Object @NotNull [] getVariants() {
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

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public PsiType @NotNull [] getTypeParameters() {
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

  @Override
  public String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }
}
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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.search.JavaDirectInheritorsSearcher;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ClsJavaCodeReferenceElementImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement {
  private final PsiElement myParent;
  private final String myCanonicalText;
  private final String myQualifiedName;
  private final ClsTypeElementImpl[] myTypeParameters;  // in right-to-left order
  private volatile PsiType[] myTypeParametersCachedTypes = null; // in left-to-right-order
  @NonNls private static final String EXTENDS_PREFIX = "?extends";
  @NonNls private static final String SUPER_PREFIX = "?super";
  public static final ClsJavaCodeReferenceElementImpl[] EMPTY_ARRAY = new ClsJavaCodeReferenceElementImpl[0];

  public ClsJavaCodeReferenceElementImpl(PsiElement parent, String canonicalText) {
    myParent = parent;

    myCanonicalText = canonicalText;
    final String[] classParametersText = PsiNameHelper.getClassParametersText(canonicalText);
    int length = classParametersText.length;
    myTypeParameters = length == 0 ? ClsTypeElementImpl.EMPTY_ARRAY : new ClsTypeElementImpl[length];
    for (int i = 0; i < length; i++) {
      String s = classParametersText[length - i - 1];
      char variance = ClsTypeElementImpl.VARIANCE_NONE;
      if (s.startsWith(EXTENDS_PREFIX)) {
        variance = ClsTypeElementImpl.VARIANCE_EXTENDS;
        s = s.substring(EXTENDS_PREFIX.length());
      }
      else if (s.startsWith(SUPER_PREFIX)) {
        variance = ClsTypeElementImpl.VARIANCE_SUPER;
        s = s.substring(SUPER_PREFIX.length());
      }
      else if (StringUtil.startsWithChar(s, '?')) {
        variance = ClsTypeElementImpl.VARIANCE_INVARIANT;
        s = s.substring(1);
      }

      myTypeParameters[i] = new ClsTypeElementImpl(this, s, variance);
    }

    myQualifiedName = PsiNameHelper.getQualifiedClassName(myCanonicalText, false);
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public String getText() {
    return PsiNameHelper.getPresentableText(this);
  }

  public int getTextLength() {
    return getText().length();
  }

  public PsiReference getReference() {
    return this;
  }

  @NotNull
  public String getCanonicalText() {
    return myCanonicalText;
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver<ClsJavaCodeReferenceElementImpl> {
    public static final Resolver INSTANCE = new Resolver();

    public JavaResolveResult[] resolve(ClsJavaCodeReferenceElementImpl ref, boolean incompleteCode) {
      final JavaResolveResult resolveResult = ref.advancedResolveImpl();
      return resolveResult.getElement() == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[] {resolveResult};
    }
  }

  private JavaResolveResult advancedResolveImpl() {
    final PsiElement resolve = resolveElement();
    if (resolve instanceof PsiClass) {
      final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
      int index = 0;
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable((PsiClass)resolve)) {
        if (index >= myTypeParameters.length) {
          substitutionMap.put(parameter, null);
        }
        else {
          substitutionMap.put(parameter, myTypeParameters[index].getType());
        }
        index++;
      }
      return new CandidateInfo(resolve, PsiSubstitutorImpl.createSubstitutor(substitutionMap));
    }
    else {
      return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
    }
  }


  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveCache resolveCache = ((PsiManagerEx)getManager()).getResolveCache();
    ResolveResult[] results = resolveCache.resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode);
    return (JavaResolveResult[])results;
  }

  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  private PsiElement resolveElement() {
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
    return resolveClassPreferringMyJar();
  }

  private PsiClass resolveClassPreferringMyJar() {
    PsiClass[] classes = JavaPsiFacade.getInstance(getProject()).findClasses(myQualifiedName, getResolveScope());
    for (PsiClass aClass : classes) {
      if (JavaDirectInheritorsSearcher.isFromTheSameJar(aClass, this)) return aClass;
    }
    return classes.length == 0 ? null : classes[0];
  }

  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    return PsiNameHelper.getShortClassName(myCanonicalText);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myCanonicalText.equals(aClass.getQualifiedName())
           || getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  public boolean isSoft() {
    return false;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append(getCanonicalText());
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, JavaElementType.JAVA_CODE_REFERENCE);
  }

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

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    PsiType[] cachedTypes = myTypeParametersCachedTypes;
    if (cachedTypes == null) {
      cachedTypes = myTypeParameters.length == 0 ? PsiType.EMPTY_ARRAY : new PsiType[myTypeParameters.length];
      for (int i = 0; i < cachedTypes.length; i++) {
        cachedTypes[cachedTypes.length - i - 1] = myTypeParameters[i].getType();
      }
      myTypeParametersCachedTypes = cachedTypes;
    }
    return cachedTypes;
  }

  public boolean isQualified() {
    return false;
  }

  public PsiElement getQualifier() {
    return null;
  }
}

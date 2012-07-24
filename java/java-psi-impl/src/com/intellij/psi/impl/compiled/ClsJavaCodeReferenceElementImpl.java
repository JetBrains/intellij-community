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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
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
import org.jetbrains.annotations.Nullable;

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

  private static class Resolver implements ResolveCache.PolyVariantResolver<ClsJavaCodeReferenceElementImpl> {
    public static final Resolver INSTANCE = new Resolver();

    @NotNull
    @Override
    public JavaResolveResult[] resolve(@NotNull ClsJavaCodeReferenceElementImpl ref, boolean incompleteCode) {
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
          if (parameter.getOwner() == resolve) substitutionMap.put(parameter, null);
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
    final ResolveCache resolveCache = ResolveCache.getInstance(getProject());
    ResolveResult[] results = resolveCache.resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode);
    if (results.length == 0) return JavaResolveResult.EMPTY_ARRAY;
    return (JavaResolveResult[])results;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Nullable
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

  @Nullable
  private PsiClass resolveClassPreferringMyJar() {
    PsiClass[] classes = JavaPsiFacade.getInstance(getProject()).findClasses(myQualifiedName, getResolveScope());
    if (classes.length == 0) return null;

    if (classes.length > 1) {
      VirtualFile jarFile = PsiUtil.getJarFile(this);
      if (jarFile != null) {
        for (PsiClass aClass : classes) {
          if (Comparing.equal(PsiUtil.getJarFile(aClass), jarFile)) return aClass;
        }
      }
    }
    return classes[0];
  }

  @Override
  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
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
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myCanonicalText.equals(aClass.getQualifiedName())
           || getManager().areElementsEquivalent(resolve(), element);
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
  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append(getCanonicalText());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) {
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

  @Override
  public boolean isQualified() {
    return false;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }
}

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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaPsiClassReferenceElement extends LookupItem<Object> {
  private final Object myClass;
  private final String myQualifiedName;

  public JavaPsiClassReferenceElement(PsiClass psiClass) {
    super(psiClass.getName(), psiClass.getName());
    myClass = psiClass.getContainingFile().getVirtualFile() == null ? psiClass : PsiAnchor.create(psiClass);
    myQualifiedName = psiClass.getQualifiedName();
    JavaCompletionUtil.setShowFQN(this);
    setInsertHandler(AllClassesGetter.TRY_SHORTENING);
    setTailType(TailType.NONE);
  }

  @NotNull
  @Override
  public PsiClass getObject() {
    if (myClass instanceof PsiAnchor) {
      final PsiClass retrieve = (PsiClass)((PsiAnchor)myClass).retrieve();
      assert retrieve != null : myQualifiedName;
      return retrieve;
    }
    return (PsiClass)myClass;
  }

  @Override
  public boolean isValid() {
    if (myClass instanceof PsiClass) {
      return ((PsiClass)myClass).isValid();
    }

    return ((PsiAnchor)myClass).retrieve() != null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaPsiClassReferenceElement)) return false;

    final JavaPsiClassReferenceElement that = (JavaPsiClassReferenceElement)o;

    return Comparing.equal(myQualifiedName, that.myQualifiedName);
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }
}

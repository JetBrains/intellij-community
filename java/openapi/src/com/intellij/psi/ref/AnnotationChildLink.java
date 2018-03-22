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
package com.intellij.psi.ref;

import com.intellij.lang.jvm.JvmAnnotatedElement;
import com.intellij.lang.jvm.JvmAnnotation;
import com.intellij.psi.PsiChildLink;
import com.intellij.psi.PsiElementRef;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author peter
 */
public class AnnotationChildLink extends PsiChildLink<JvmAnnotatedElement, JvmAnnotation> {
  private final String myAnnoFqn;

  public AnnotationChildLink(String fqn) {
    myAnnoFqn = fqn;
  }

  public String getAnnotationQualifiedName() {
    return myAnnoFqn;
  }

  public static PsiElementRef<JvmAnnotation> createRef(@NotNull JvmAnnotatedElement parent, @NonNls String fqn) {
    return new AnnotationChildLink(fqn).createChildRef(parent);
  }

  @Override
  public JvmAnnotation findLinkedChild(@Nullable JvmAnnotatedElement member) {
    if (member == null) return null;

    final JvmAnnotation[] modifierList = member.getAnnotations();
    return Arrays.stream(modifierList).filter(a -> a.getQualifiedName().equals(myAnnoFqn)).findAny().orElse(null);
  }

  //@Override
  @NotNull
  public JvmAnnotation createChild(@NotNull JvmAnnotatedElement member) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Not implemented");
    //final PsiModifierList modifierList = member.getModifierList();
    //assert modifierList != null;
    //return modifierList.addAnnotation(myAnnoFqn);
  }

  @Override
  public String toString() {
    return "AnnotationChildLink{" + "myAnnoFqn='" + myAnnoFqn + '\'' + '}';
  }
}

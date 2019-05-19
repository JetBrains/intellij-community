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
package com.intellij.refactoring.psi;

import com.intellij.psi.*;

import java.util.Set;

@SuppressWarnings({"AssignmentToCollectionOrArrayFieldFromParameter"})
public class TypeParametersVisitor extends JavaRecursiveElementWalkingVisitor {
   private final Set<? super PsiTypeParameter> params;

   public TypeParametersVisitor(Set<? super PsiTypeParameter> params) {
       super();
       this.params = params;
   }

   @Override
   public void visitTypeElement(PsiTypeElement typeElement) {
       super.visitTypeElement(typeElement);
       final PsiType type = typeElement.getType();
       if (type instanceof PsiClassType) {
           final PsiClass referent = ((PsiClassType) type).resolve();
           if (referent instanceof PsiTypeParameter) {
               params.add((PsiTypeParameter) referent);
           }
       }
   }

}

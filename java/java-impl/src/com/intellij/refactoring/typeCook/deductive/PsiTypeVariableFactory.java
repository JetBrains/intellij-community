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
package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author db
 */
public class PsiTypeVariableFactory {
  private int myCurrent;
  private final List<Set<PsiTypeVariable>> myClusters = new LinkedList<>();
  private final Map<Integer, Set<PsiTypeVariable>> myVarCluster = new HashMap<>();

  public final int getNumber() {
    return myCurrent;
  }

  public final void registerCluster(final Set<PsiTypeVariable> cluster) {
    myClusters.add(cluster);

    for (final PsiTypeVariable aCluster : cluster) {
      myVarCluster.put(new Integer(aCluster.getIndex()), cluster);
    }
  }

  public final List<Set<PsiTypeVariable>> getClusters() {
    return myClusters;
  }

  public final Set<PsiTypeVariable> getClusterOf(final int var) {
    return myVarCluster.get(new Integer(var));
  }

  public final PsiTypeVariable create() {
    return create(null);
  }

  public final PsiTypeVariable create(final PsiElement context) {
    return new PsiTypeVariable() {
      private final int myIndex = myCurrent++;
      private final PsiElement myContext = context;

      @Override
      public boolean isValidInContext(final PsiType type) {
        if (myContext == null) {
          return true;
        }

        if (type == null) {
          return true;
        }

        return type.accept(new PsiTypeVisitor<Boolean>() {
          @Override
          public Boolean visitType(final PsiType type) {
            return Boolean.TRUE;
          }

          @Override
          public Boolean visitArrayType(final PsiArrayType arrayType) {
            return arrayType.getDeepComponentType().accept(this);
          }

          @Override
          public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
            final PsiType bound = wildcardType.getBound();

            if (bound != null) {
              bound.accept(this);
            }

            return Boolean.TRUE;
          }

          @Override
          public Boolean visitClassType(final PsiClassType classType) {
            final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            final PsiClass aClass = result.getElement();
            final PsiSubstitutor aSubst = result.getSubstitutor();

            if (aClass != null) {
              final PsiManager manager = aClass.getManager();
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

              if (aClass instanceof PsiTypeParameter) {
                final PsiTypeParameterListOwner owner = PsiTreeUtil.getParentOfType(myContext, PsiTypeParameterListOwner.class);

                if (owner != null) {
                  boolean found = false;

                  for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(owner)) {
                    found = manager.areElementsEquivalent(typeParameter, aClass);
                    if (found) break;
                  }

                  if (!found) {
                    return Boolean.FALSE;
                  }
                }
                else {
                  return Boolean.FALSE;
                }
              }
              else if (!facade.getResolveHelper().isAccessible(aClass, myContext, null)) {
                return Boolean.FALSE;
              }

              for (PsiTypeParameter parm : PsiUtil.typeParametersIterable(aClass)) {
                final PsiType type = aSubst.substitute(parm);

                if (type != null) {
                  final Boolean b = type.accept(this);

                  if (!b.booleanValue()) {
                    return Boolean.FALSE;
                  }
                }
              }

              return Boolean.TRUE;
            }
            else {
              return Boolean.FALSE;
            }
          }
        }).booleanValue();
      }

      @Override
      @NotNull
      public String getPresentableText() {
        return "$" + myIndex;
      }

      @Override
      @NotNull
      public String getCanonicalText() {
        return getPresentableText();
      }

      @Override
      @NotNull
      public String getInternalCanonicalText() {
        return getCanonicalText();
      }

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public boolean equalsToText(@NotNull String text) {
        return text.equals(getPresentableText());
      }

      @Override
      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @Override
      @NotNull
      public PsiType[] getSuperTypes() {
        return EMPTY_ARRAY;
      }

      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PsiTypeVariable)) return false;

        final PsiTypeVariable psiTypeVariable = (PsiTypeVariable)o;

        if (myIndex != psiTypeVariable.getIndex()) return false;

        return true;
      }

      public int hashCode() {
        return myIndex;
      }

      @Override
      public int getIndex() {
        return myIndex;
      }
    };
  }
}

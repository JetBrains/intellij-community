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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class PsiMethodWithOverridingPercentMember extends PsiMethodMember {

  private final int myOverridingPercent;

  public PsiMethodWithOverridingPercentMember(final CandidateInfo info, final int overridingPercent) {
    super(info);
    myOverridingPercent = overridingPercent;
  }

  @Override
  public void renderTreeNode(final SimpleColoredComponent component, final JTree tree) {
    component.append(myOverridingPercent + "% ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    super.renderTreeNode(component, tree);
  }

  @TestOnly
  public int getOverridingPercent() {
    return myOverridingPercent;
  }

  public static final Comparator<PsiMethodMember> COMPARATOR = (e1, e2) -> {
    if (!(e1 instanceof PsiMethodWithOverridingPercentMember)) {
      if (!(e2 instanceof PsiMethodWithOverridingPercentMember)) {
        return e1.equals(e2) ? 0 : -1;
      } else {
        return -1;
      }
    }


    if (!(e2 instanceof PsiMethodWithOverridingPercentMember)) {
      return 1;
    }
    int sub =
      ((PsiMethodWithOverridingPercentMember)e2).myOverridingPercent - ((PsiMethodWithOverridingPercentMember)e1).myOverridingPercent;
    if (sub != 0) return sub;
    return String.CASE_INSENSITIVE_ORDER.compare(e1.getText(), e2.getText());
  };

  @NotNull
  public static PsiMethodWithOverridingPercentMember[] calculateOverridingPercents(@NotNull final Collection<CandidateInfo> candidateInfos) {
    final List<PsiMethodWithOverridingPercentMember> result = new ArrayList<>(candidateInfos.size());
    final Map<String, Collection<PsiClass>> classShortNames2Inheritors = new HashMap<>();
    for (final CandidateInfo candidateInfo : candidateInfos) {
      final PsiMethod method = (PsiMethod)candidateInfo.getElement();
      if (!method.hasModifierProperty(PsiModifier.FINAL) &&
          !method.isConstructor() &&
          !method.isDeprecated() &&
          !EXCLUDED_JAVA_LANG_OBJECT_METHOD_NAMES.contains(method.getName())) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          continue;
        }

        final String classShortName = containingClass.getName();

        Collection<PsiClass> allInheritors = classShortNames2Inheritors.get(classShortName);
        if (allInheritors == null) {
          allInheritors = ClassInheritorsSearch.search(containingClass).findAll();
          classShortNames2Inheritors.put(classShortName, allInheritors);
        }

        final int allInheritorsCount = allInheritors.size() - 1;
        if (allInheritorsCount > 0) {
          final int percent = searchForOverridingCount(method, allInheritors) * 100 / allInheritorsCount;
          if (percent > 1) {
            result.add(new PsiMethodWithOverridingPercentMember(candidateInfo, percent));
          }
        }
      }
    }
    return result.toArray(new PsiMethodWithOverridingPercentMember[result.size()]);
  }

  private static int searchForOverridingCount(final PsiMethod method, final Collection<PsiClass> containingClassInheritors) {
    int counter = 0;
    for (final PsiClass inheritor : containingClassInheritors) {
      if (inheritor instanceof PsiExtensibleClass) {
        final List<PsiMethod> ownMethods = ((PsiExtensibleClass)inheritor).getOwnMethods();
        for (PsiMethod ownMethod : ownMethods) {
          if (maybeSuper(method, ownMethod)) {
            counter++;
            break;
          }
        }

      }
    }
    return counter;
  }

  private static boolean maybeSuper(@NotNull final PsiMethod superMethod, @NotNull final PsiMethod method) {
    if (!superMethod.getName().equals(method.getName())) {
      return false;
    }
    final PsiParameterList superMethodParameterList = superMethod.getParameterList();
    final PsiParameterList methodParameterList = method.getParameterList();
    if (superMethodParameterList.getParametersCount() != methodParameterList.getParametersCount()) {
      return false;
    }
    final PsiParameter[] superMethodParameters = superMethodParameterList.getParameters();
    final PsiParameter[] methodParameters = methodParameterList.getParameters();
    for (int i = 0; i < methodParameters.length; i++) {
      if (!StringUtil.equals(getTypeShortName(superMethodParameters[i].getType()), getTypeShortName(methodParameters[i].getType()))) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static String getTypeShortName(@NotNull final PsiType type) {
    if (type instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)type).getBoxedTypeName();
    }
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).getClassName();
    }
    if (type instanceof PsiArrayType) {
      return getTypeShortName(((PsiArrayType)type).getComponentType()) + "[]";
    }
    return null;
  }

  private static final Set<String> EXCLUDED_JAVA_LANG_OBJECT_METHOD_NAMES =
    ContainerUtil.newHashSet("hashCode", "finalize", "clone", "equals", "toString");

  @Override
  public String toString() {
    return "PsiMethodWithOverridingPercentMember{" +
           "myOverridingPercent=" + myOverridingPercent + ", myElement=" + getElement() +
           '}';
  }
}

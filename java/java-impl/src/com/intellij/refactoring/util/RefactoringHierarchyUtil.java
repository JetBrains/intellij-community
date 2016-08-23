/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 14:28:03
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefactoringHierarchyUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.RefactoringHierarchyUtil");

  private static final List<? extends PsiType> PRIMITIVE_TYPES = Arrays.asList(
      PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT, PsiType.LONG, PsiType.FLOAT, PsiType.DOUBLE
  );

  private RefactoringHierarchyUtil() {}

  public static boolean willBeInTargetClass(PsiElement place,
                                            @NotNull Set<? extends PsiMember> membersToMove,
                                            @Nullable PsiClass targetClass,
                                            boolean includeSubclasses) {
    PsiElement parent = place;
    while (parent != null) {
      if (membersToMove.contains(parent)) return true;
      if (parent instanceof PsiModifierList) return false; //see IDEADEV-12448
      if (parent instanceof PsiClass && targetClass != null) {
        if (targetClass.equals(parent)) return true;
        if (includeSubclasses && ((PsiClass) parent).isInheritor(targetClass, true)) return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public static PsiClass getDeepestNonObjectBase(PsiClass aClass) {
    PsiClass current = aClass;
    while (current != null) {
      PsiClassType[] supers = current.getExtendsListTypes();
      if (supers.length == 0) {
        return current;
      }
      PsiClass base = supers[0].resolve();
      if (base != null) {
        current = base;
      }
      else {
        return current;
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass getNearestBaseClass(PsiClass subClass, boolean includeNonProject) {
    PsiClassType[] superTypes = subClass.getSuperTypes();

    if (superTypes.length > 0) {
      PsiClass resolved = superTypes[0].resolve();
      // if we have no superclass but have interfaces, prefer interfaces to class (IDEADEV-20104)
      if (resolved != null && CommonClassNames.JAVA_LANG_OBJECT.equals(resolved.getQualifiedName()) && superTypes.length > 1) {
        resolved = superTypes [1].resolve();
      }
      if (resolved != null) {
        if (!includeNonProject) {
          if (resolved.getManager().isInProject(resolved)) {
            return resolved;
          }
        }
        else {
          return resolved;
        }
      }
    }
    return null;
  }

  /**
   *
   * @param subClass
   * @param includeNonProject
   * @param sortAlphabetically if false, sorted in DFS order
   * @return
   */
  public static ArrayList<PsiClass> createBasesList(PsiClass subClass, boolean includeNonProject, boolean sortAlphabetically) {
    LinkedHashSet<PsiClass> bases = new LinkedHashSet<>();
    InheritanceUtil.getSuperClasses(subClass, bases, includeNonProject);

    if (!subClass.isInterface()) {
      final PsiManager manager = subClass.getManager();
      PsiClass javaLangObject = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, subClass.getResolveScope());
      if (includeNonProject && javaLangObject != null && !manager.areElementsEquivalent(javaLangObject, subClass)) {
        bases.add(javaLangObject);
      }
    }

    ArrayList<PsiClass> basesList = new ArrayList<>(bases);

    if (sortAlphabetically) {
      Collections.sort(
        basesList, (c1, c2) -> {
          final String fqn1 = c1.getQualifiedName();
          final String fqn2 = c2.getQualifiedName();
          if (fqn1 != null && fqn2 != null) return fqn1.compareTo(fqn2);
          if (fqn1 == null && fqn2 == null) {
            return Comparing.compare(c1.getName(), c2.getName());
          }
          return fqn1 == null ? 1 : -1;
        }
      );
    }

    return basesList;
  }

  /**
   * Checks whether given element is below the given superClass in class hierarchy.
   * @param superClass
   * @return
   * @param subClass
   * @param member
   */
  public static boolean isMemberBetween(PsiClass superClass, PsiClass subClass, PsiMember member) {
    PsiClass elementClass = null;
    if (member instanceof PsiField || member instanceof PsiMethod) {
      elementClass = member.getContainingClass();
    }

    if (elementClass == null) return false;
    if (superClass != null) {
      if (elementClass.isInheritor(superClass, true)) {
        return !superClass.getManager().areElementsEquivalent(superClass, elementClass);
      }
      return PsiTreeUtil.isAncestor(elementClass, subClass, false) && !PsiTreeUtil.isAncestor(elementClass, superClass, false);
    }
    else {
      return subClass.getManager().areElementsEquivalent(subClass, elementClass);
    }
  }

  public static void processSuperTypes(PsiType type, SuperTypeVisitor visitor) {
    processSuperTypes(type, visitor, new HashSet<>());
  }
  private static void processSuperTypes(PsiType type, SuperTypeVisitor visitor, Set<PsiType> visited) {
    if (visited.contains(type)) return;
    visited.add(type);
    if (type instanceof PsiPrimitiveType) {
      int index = PRIMITIVE_TYPES.indexOf(type);
      if (index >= 0) {
        for (int i = index + 1; i < PRIMITIVE_TYPES.size(); i++) {
          visitor.visitType(PRIMITIVE_TYPES.get(i));
        }
      }
    }
    else {
      final PsiType[] superTypes = type.getSuperTypes();
      for (PsiType superType : superTypes) {
        visitor.visitType(superType);
        processSuperTypes(superType, visitor, visited);
      }
    }
  }

  public static PsiClass[] findImplementingClasses(PsiClass anInterface) {
    Set<PsiClass> result = new HashSet<>();
    _findImplementingClasses(anInterface, new HashSet<>(), result);
    boolean classesRemoved = true;
    while(classesRemoved) {
      classesRemoved = false;
      loop1:
      for (Iterator<PsiClass> iterator = result.iterator(); iterator.hasNext();) {
        final PsiClass psiClass = iterator.next();
        for (final PsiClass aClass : result) {
          if (psiClass.isInheritor(aClass, true)) {
            iterator.remove();
            classesRemoved = true;
            break loop1;
          }
        }
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  private static void _findImplementingClasses(PsiClass anInterface, final Set<PsiClass> visited, final Collection<PsiClass> result) {
    LOG.assertTrue(anInterface.isInterface());
    visited.add(anInterface);
    ClassInheritorsSearch.search(anInterface, false).forEach(new PsiElementProcessorAdapter<>(new PsiElementProcessor<PsiClass>() {
      public boolean execute(@NotNull PsiClass aClass) {
        if (!aClass.isInterface()) {
          result.add(aClass);
        }
        else if (!visited.contains(aClass)) {
          _findImplementingClasses(aClass, visited, result);
        }
        return true;
      }
    }));
  }


  public interface SuperTypeVisitor {
    void visitType(PsiType aType);

    void visitClass(PsiClass aClass);
  }
}

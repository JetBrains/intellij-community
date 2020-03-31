// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefactoringHierarchyUtil {
  private static final Logger LOG = Logger.getInstance(RefactoringHierarchyUtil.class);

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
      //noinspection SuspiciousMethodCalls
      if (membersToMove.contains(parent)) return true;
      if (parent instanceof PsiModifierList && (targetClass == null || targetClass.getModifierList() == parent)) return false; //see IDEADEV-12448
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
      basesList.sort((c1, c2) -> {
        final String fqn1 = c1.getQualifiedName();
        final String fqn2 = c2.getQualifiedName();
        if (fqn1 != null && fqn2 != null) return fqn1.compareTo(fqn2);
        if (fqn1 == null && fqn2 == null) {
          return Comparing.compare(c1.getName(), c2.getName());
        }
        return fqn1 == null ? 1 : -1;
      });
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
    if (type instanceof PsiPrimitiveType) {
      int index = PRIMITIVE_TYPES.indexOf(type);
      if (index >= 0) {
        for (int i = index + 1; i < PRIMITIVE_TYPES.size(); i++) {
          visitor.visitType(PRIMITIVE_TYPES.get(i));
        }
      }
    }
    else {
      InheritanceUtil.processSuperTypes(type, false, aType -> {
        visitor.visitType(aType);
        return true;
      });
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
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static void _findImplementingClasses(PsiClass anInterface, final Set<? super PsiClass> visited, final Collection<? super PsiClass> result) {
    LOG.assertTrue(anInterface.isInterface());
    visited.add(anInterface);
    ClassInheritorsSearch.search(anInterface, false).forEach(new PsiElementProcessorAdapter<>(new PsiElementProcessor<PsiClass>() {
      @Override
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

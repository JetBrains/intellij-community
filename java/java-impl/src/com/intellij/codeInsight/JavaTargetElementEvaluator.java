/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.*;
import com.intellij.util.BitUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaTargetElementEvaluator extends TargetElementEvaluatorEx2 implements TargetElementUtilExtender{
  public static final int NEW_AS_CONSTRUCTOR = 0x04;
  public static final int THIS_ACCEPTED = 0x10;
  public static final int SUPER_ACCEPTED = 0x20;
  public static final int USE_DFA = 0x40;

  @Override
  public int getAllAdditionalFlags() {
    return NEW_AS_CONSTRUCTOR | THIS_ACCEPTED | SUPER_ACCEPTED | USE_DFA;
  }

  /**
   * Accepts THIS or SUPER or USE_DFA but not NEW_AS_CONSTRUCTOR.
   */
  @Override
  public int getAdditionalDefinitionSearchFlags() {
    return THIS_ACCEPTED | SUPER_ACCEPTED | USE_DFA;
  }

  /**
   * Accepts NEW_AS_CONSTRUCTOR but not THIS or SUPER.
   */
  @Override
  public int getAdditionalReferenceSearchFlags() {
    return NEW_AS_CONSTRUCTOR;
  }

  @Nullable
  @Override
  public PsiElement adjustTargetElement(Editor editor, int offset, int flags, @NotNull PsiElement targetElement) {
    if (targetElement instanceof PsiKeyword) {
      if (targetElement.getParent() instanceof PsiThisExpression) {
        if (!BitUtil.isSet(flags, THIS_ACCEPTED)) return null;
        PsiType type = ((PsiThisExpression)targetElement.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }

      if (targetElement.getParent() instanceof PsiSuperExpression) {
        if (!BitUtil.isSet(flags, SUPER_ACCEPTED)) return null;
        PsiType type = ((PsiSuperExpression)targetElement.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }
    }
    if (targetElement instanceof PsiMethod && BitUtil.isSet(flags, USE_DFA)) {
      PsiElement realMethod = findOverridingMethod(editor, offset, (PsiMethod)targetElement);
      if (realMethod != null) return realMethod;
    }
    return super.adjustTargetElement(editor, offset, flags, targetElement);
  }

  @Nullable
  private static PsiElement findOverridingMethod(Editor editor, int offset, PsiMethod method) {
    PsiClass qualifierClass = method.getContainingClass();
    if (qualifierClass == null || !PsiUtil.canBeOverridden(method)) return null;
    PsiReference reference = TargetElementUtil.findReference(editor, offset);
    if (!(reference instanceof PsiReferenceExpression) || !reference.isReferenceTo(method)) return null;
    PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
    if (qualifier == null) return null;
    if (DirectClassInheritorsSearch.search(qualifierClass, qualifier.getResolveScope(), false).findFirst() == null) {
      return null;
    }
    TypeConstraint constraint = CommonDataflow.getExpressionFact(qualifier, DfaFactType.TYPE_CONSTRAINT);
    if (constraint == null) return null;
    return MethodUtils.findSpecificMethod(method, constraint.getPsiType());
  }

  @Override
  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return !(parent instanceof PsiDocTag);
  }

  @Override
  @NotNull
  public ThreeState isAcceptableReferencedElement(@NotNull final PsiElement element, final PsiElement referenceOrReferencedElement) {
    if (referenceOrReferencedElement instanceof SyntheticElement) return ThreeState.NO;
    if (isEnumConstantReference(element, referenceOrReferencedElement)) return ThreeState.NO;
    return super.isAcceptableReferencedElement(element, referenceOrReferencedElement);
  }

  private static boolean isEnumConstantReference(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return element != null &&
           element.getParent() instanceof PsiEnumConstant &&
           referenceOrReferencedElement instanceof PsiMethod &&
           ((PsiMethod)referenceOrReferencedElement).isConstructor();
  }

  @Nullable
  @Override
  public PsiElement getElementByReference(@NotNull PsiReference ref, int flags) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement adjustReferenceOrReferencedElement(PsiFile file,
                                                       Editor editor,
                                                       int offset,
                                                       int flags,
                                                       @Nullable PsiElement refElement) {
    PsiReference ref = null;
    if (refElement == null) {
      ref = TargetElementUtil.findReference(editor, offset);
      if (ref instanceof PsiJavaReference) {
        refElement = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      }
      else if (ref == null) {
        final PsiElement element = file.findElementAt(offset);
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiFunctionalExpression) {
            refElement = PsiUtil.resolveClassInType(((PsiFunctionalExpression)parent).getFunctionalInterfaceType());
          }
        } 
      }
    }

    if (refElement != null) {
      if (BitUtil.isSet(flags, NEW_AS_CONSTRUCTOR)) {
        if (ref == null) {
          ref = TargetElementUtil.findReference(editor, offset);
        }
        if (ref != null) {
          PsiElement parent = ref.getElement().getParent();
          if (parent instanceof PsiAnonymousClass) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiNewExpression) {
            PsiMethod constructor = ((PsiNewExpression)parent).resolveConstructor();
            if (constructor != null) {
              refElement = constructor;
            } else if (refElement instanceof PsiClass && ((PsiClass)refElement).getConstructors().length > 0) {
              return null;
            }
          }
        }
      }

      if (refElement instanceof PsiMirrorElement) {
        return ((PsiMirrorElement)refElement).getPrototype();
      }

      if (refElement instanceof PsiClass) {
        final PsiFile containingFile = refElement.getContainingFile();
        if (containingFile != null && containingFile.getVirtualFile() == null) { // in mirror file of compiled class
          String qualifiedName = ((PsiClass)refElement).getQualifiedName();
          if (qualifiedName == null) return null;
          return JavaPsiFacade.getInstance(refElement.getProject()).findClass(qualifiedName, refElement.getResolveScope());
        }
      }
    }
    return super.adjustReferenceOrReferencedElement(file, editor, offset, flags, refElement);
  }

  @Nullable
  @Override
  public PsiElement getNamedElement(@NotNull PsiElement element) {
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiClass && element.equals(((PsiClass)parent).getNameIdentifier()) ||
          parent instanceof PsiVariable && element.equals(((PsiVariable)parent).getNameIdentifier()) ||
          parent instanceof PsiMethod && element.equals(((PsiMethod)parent).getNameIdentifier()) ||
          parent instanceof PsiLabeledStatement && element.equals(((PsiLabeledStatement)parent).getLabelIdentifier())) {
        return parent;
      }
      if (parent instanceof PsiJavaModuleReferenceElement) {
        PsiElement grand = parent.getParent();
        if (grand instanceof PsiJavaModule) {
          return grand;
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiReferenceExpression findReferenceExpression(Editor editor) {
    final PsiReference ref = TargetElementUtil.findReference(editor);
    return ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
  }

  @Nullable
  @Override
  public PsiElement adjustReference(@NotNull final PsiReference ref) {
    final PsiElement parent = ref.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) return parent;
    return super.adjustReference(ref);
  }

  @Nullable
  @Override
  public PsiElement adjustElement(Editor editor, int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    if (element instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)element).getBaseClassType().resolve();
    }
    return element;
  }

  @Override
  @Nullable
  public Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    PsiElement parent = reference.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression || parent instanceof PsiNewExpression && 
                                                     ((PsiNewExpression)parent).getArrayDimensions().length == 0 &&
                                                     ((PsiNewExpression)parent).getArrayInitializer() == null) {
      PsiCallExpression callExpr = (PsiCallExpression)parent;
      boolean allowStatics = false;
      PsiExpression qualifier = callExpr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)callExpr).getMethodExpression().getQualifierExpression()
                                                                            : ((PsiNewExpression)callExpr).getQualifier();
      if (qualifier == null) {
        allowStatics = true;
      }
      else if (qualifier instanceof PsiJavaCodeReferenceElement) {
        PsiElement referee = ((PsiJavaCodeReferenceElement)qualifier).advancedResolve(true).getElement();
        if (referee instanceof PsiClass) allowStatics = true;
      }
      PsiResolveHelper helper = JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper();
      PsiElement[] candidates = PsiUtil.mapElements(helper.getReferencedMethodCandidates(callExpr, false));
      final Collection<PsiElement> methods = new LinkedHashSet<>();
      for (PsiElement candidate1 : candidates) {
        PsiMethod candidate = (PsiMethod)candidate1;
        if (candidate.hasModifierProperty(PsiModifier.STATIC) && !allowStatics) continue;
        List<PsiMethod> supers = Arrays.asList(candidate.findSuperMethods());
        if (supers.isEmpty()) {
          methods.add(candidate);
        }
        else {
          methods.addAll(supers);
        }
      }
      return methods;
    }

    return super.getTargetCandidates(reference);
  }

  @Override
  @Nullable
  public PsiElement getGotoDeclarationTarget(@NotNull final PsiElement element, @Nullable final PsiElement navElement) {
    if (navElement == element && element instanceof PsiCompiledElement && element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() && method.getParameterList().getParametersCount() == 0) {
        PsiClass aClass = method.getContainingClass();
        PsiElement navClass = aClass == null ? null : aClass.getNavigationElement();
        if (aClass != navClass) return navClass;
      }
    }
    return super.getGotoDeclarationTarget(element, navElement);
  }

  @Override
  public boolean includeSelfInGotoImplementation(@NotNull final PsiElement element) {
    if (element instanceof PsiModifierListOwner && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    return super.includeSelfInGotoImplementation(element);
  }

  @Override
  public boolean acceptImplementationForReference(@Nullable PsiReference reference, @NotNull PsiElement element) {
    if (reference instanceof PsiReferenceExpression && element instanceof PsiMember) {
      return getClassesWithMember(reference, (PsiMember)element) != null;
    }
    return super.acceptImplementationForReference(reference, element);
  }

  @Nullable
  private static PsiClass[] getClassesWithMember(final PsiReference reference, final PsiMember member) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
      @Override
      public PsiClass[] compute() {
        PsiClass containingClass = member.getContainingClass();
        final PsiExpression expression = ((PsiReferenceExpression)reference).getQualifierExpression();
        PsiClass psiClass;
        if (reference instanceof PsiMethodReferenceExpression) {
          psiClass = PsiMethodReferenceUtil.getQualifierResolveResult((PsiMethodReferenceExpression)reference).getContainingClass();
        }
        else if (expression != null) {
          psiClass = PsiUtil.resolveClassInType(expression.getType());
        }
        else {
          if (member instanceof PsiClass) {
            psiClass = (PsiClass)member;
            final PsiElement resolve = ((PsiReferenceExpression)reference).advancedResolve(true).getElement();
            if (resolve instanceof PsiClass) {
              containingClass = (PsiClass)resolve;
            }
          } else {
            psiClass = PsiTreeUtil.getParentOfType((PsiReferenceExpression)reference, PsiClass.class);
          }
        }

        if (containingClass == null && psiClass == null) return PsiClass.EMPTY_ARRAY;
        if (containingClass != null) {
          PsiClass[] inheritors = getInheritors(containingClass, psiClass, new HashSet<>());
          return inheritors.length == 0 ? null : inheritors;
        }
        return null;
      }

      private PsiClass[] getInheritors(PsiClass containingClass, PsiClass psiClass, Set<PsiClass> visited) {
        if (psiClass instanceof PsiTypeParameter) {
          List<PsiClass> result = new ArrayList<>();
          for (PsiClassType classType : psiClass.getExtendsListTypes()) {
            PsiClass aClass = classType.resolve();
            if (aClass != null && visited.add(aClass)) {
              ContainerUtil.addAll(result, getInheritors(containingClass, aClass, visited));
            }
          }
          return result.toArray(PsiClass.EMPTY_ARRAY);
        }

        PsiElementFindProcessor<PsiClass> processor1 = new PsiElementFindProcessor<>(containingClass);
        while (psiClass != null) {
          if (!processor1.process(psiClass) ||
              !ClassInheritorsSearch.search(containingClass).forEach(new PsiElementFindProcessor<>(psiClass)) ||
              !ClassInheritorsSearch.search(psiClass).forEach(processor1)) {
            return new PsiClass[] {psiClass};
          }
          psiClass = psiClass.getContainingClass();
        }
        return PsiClass.EMPTY_ARRAY;
      }
    });
  }

  
  @Override
  @Nullable
  public SearchScope getSearchScope(Editor editor, @NotNull final PsiElement element) {
    final PsiReferenceExpression referenceExpression = editor != null ? findReferenceExpression(editor) : null;
    if (referenceExpression != null && element instanceof PsiMethod) {
       if (!PsiUtil.canBeOverridden((PsiMethod)element)) {
         return element.getUseScope();
       }
      final PsiClass[] memberClass = getClassesWithMember(referenceExpression, (PsiMember)element);
      if (memberClass != null && memberClass.length == 1) {
        return CachedValuesManager.getCachedValue(memberClass[0], () -> {
          final List<PsiClass> classesToSearch = ContainerUtil.newArrayList(memberClass);
          classesToSearch.addAll(ClassInheritorsSearch.search(memberClass[0]).findAll());

          final Set<PsiClass> supers = new HashSet<>();
          for (PsiClass psiClass : classesToSearch) {
            supers.addAll(InheritanceUtil.getSuperClasses(psiClass));
          }

          final List<PsiElement> elements = new ArrayList<>();
          elements.addAll(classesToSearch);
          elements.addAll(supers);
          elements.addAll(FunctionalExpressionSearch.search(memberClass[0]).findAll());

          return new CachedValueProvider.Result<SearchScope>(new LocalSearchScope(PsiUtilCore.toPsiElementArray(elements)), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        });
      }
    }
    return super.getSearchScope(editor, element);
  }

  private static class PsiElementFindProcessor<T extends PsiClass> implements Processor<T> {
    private final T myElement;

    public PsiElementFindProcessor(T t) {
      myElement = t;
    }

    @Override
    public boolean process(T t) {
      if (InheritanceUtil.isInheritorOrSelf(t, myElement, true)) return false;
      return !myElement.getManager().areElementsEquivalent(myElement, t);
    }
  }
}
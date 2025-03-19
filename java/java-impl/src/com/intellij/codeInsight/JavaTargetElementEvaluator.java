// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMember;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.BitUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaTargetElementEvaluator extends TargetElementEvaluatorEx2 implements TargetElementUtilExtender{
  private static final int NEW_AS_CONSTRUCTOR = 0x04;
  private static final int THIS_ACCEPTED = 0x10;
  private static final int SUPER_ACCEPTED = 0x20;

  @Override
  public int getAllAdditionalFlags() {
    return NEW_AS_CONSTRUCTOR | THIS_ACCEPTED | SUPER_ACCEPTED;
  }

  /**
   * Accepts THIS or SUPER or USE_DFA but not NEW_AS_CONSTRUCTOR.
   */
  @Override
  public int getAdditionalDefinitionSearchFlags() {
    return THIS_ACCEPTED | SUPER_ACCEPTED;
  }

  /**
   * Accepts NEW_AS_CONSTRUCTOR but not THIS or SUPER.
   */
  @Override
  public int getAdditionalReferenceSearchFlags() {
    return NEW_AS_CONSTRUCTOR;
  }

  @Override
  public @Nullable PsiElement adjustTargetElement(Editor editor, int offset, int flags, @NotNull PsiElement targetElement) {
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
    return super.adjustTargetElement(editor, offset, flags, targetElement);
  }

  @Override
  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return !(parent instanceof PsiDocTag) && !(parent instanceof PsiAnonymousClass);
  }

  @Override
  public @NotNull ThreeState isAcceptableReferencedElement(final @NotNull PsiElement element, final PsiElement referenceOrReferencedElement) {
    if (isEnumConstantReference(element, referenceOrReferencedElement)) return ThreeState.NO;
    return super.isAcceptableReferencedElement(element, referenceOrReferencedElement);
  }

  private static boolean isEnumConstantReference(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return element != null &&
           element.getParent() instanceof PsiEnumConstant &&
           referenceOrReferencedElement instanceof PsiMethod && ((PsiMethod)referenceOrReferencedElement).isConstructor();
  }

  @Override
  public @Nullable PsiElement adjustReferenceOrReferencedElement(@NotNull PsiFile file,
                                                                 @NotNull Editor editor,
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
          if (parent instanceof PsiFunctionalExpression &&
              (PsiUtil.isJavaToken(element, JavaTokenType.ARROW) || PsiUtil.isJavaToken(element, JavaTokenType.DOUBLE_COLON))) {
            refElement = LambdaUtil.resolveFunctionalInterfaceClass((PsiFunctionalExpression)parent);
          }
          else if (element instanceof PsiKeyword &&
                   parent instanceof PsiTypeElement &&
                   ((PsiTypeElement)parent).isInferredType()) {
            refElement = PsiUtil.resolveClassInType(((PsiTypeElement)parent).getType());
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
          PsiElement element = ref.getElement();
          PsiElement parent = element.getParent();
          if (parent instanceof PsiAnonymousClass) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiNewExpression && element != ((PsiNewExpression)parent).getQualifier()) {
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

      if (refElement instanceof PsiExtensionMethod) {
        refElement = ((PsiExtensionMethod)refElement).getTargetMethod();
      }

      if (refElement instanceof LightRecordMember) {
        return ((LightRecordMember)refElement).getRecordComponent();
      }

      if (refElement instanceof LightRecordCanonicalConstructor) {
        return ((LightRecordCanonicalConstructor)refElement).getContainingClass();
      }
    }
    return super.adjustReferenceOrReferencedElement(file, editor, offset, flags, refElement);
  }

  @Override
  public @Nullable PsiElement getNamedElement(@NotNull PsiElement element) {
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiClass psiClass && element.equals(psiClass.getNameIdentifier()) ||
          parent instanceof PsiVariable psiVariable && element.equals(psiVariable.getNameIdentifier()) ||
          parent instanceof PsiMethod psiMethod && element.equals(psiMethod.getNameIdentifier()) ||
          parent instanceof PsiLabeledStatement labeledStatement && element.equals(labeledStatement.getLabelIdentifier())) {
        return parent;
      }
      if (parent instanceof PsiJavaModuleReferenceElement) {
        PsiElement grand = parent.getParent();
        if (grand instanceof PsiJavaModule) {
          return grand;
        }
      }
    }
    PsiElement headerCandidate =
      PsiUtil.isJavaToken(element, JavaTokenType.LPARENTH) ? element.getParent() :
      element instanceof PsiWhiteSpace ? PsiTreeUtil.skipWhitespacesAndCommentsForward(element) :
      null;
    if (headerCandidate instanceof PsiRecordHeader header && header.getParent() instanceof PsiClass recordClass) {
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
      if (constructor instanceof SyntheticElement) {
        return constructor;
      }
      return recordClass;
    }

    return null;
  }

  public static @Nullable PsiReferenceExpression findReferenceExpression(Editor editor) {
    final PsiReference ref = TargetElementUtil.findReference(editor);
    return ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
  }

  @Override
  public @Nullable PsiElement adjustReference(final @NotNull PsiReference ref) {
    final PsiElement parent = ref.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) return parent;
    return super.adjustReference(ref);
  }

  @Override
  public @Nullable PsiElement adjustElement(Editor editor, int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    if (element instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)element).getBaseClassType().resolve();
    }
    return element;
  }

  @Override
  public @Nullable Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    PsiElement parent = reference.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression ||
        parent instanceof PsiNewExpression && !((PsiNewExpression)parent).isArrayCreation()) {
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
  public @Nullable PsiElement getGotoDeclarationTarget(final @NotNull PsiElement element, final @Nullable PsiElement navElement) {
    if (navElement == element && element instanceof PsiCompiledElement &&
        element instanceof PsiMethod method && method.isConstructor() && method.getParameterList().isEmpty()) {
      PsiClass aClass = method.getContainingClass();
      PsiElement navClass = aClass == null ? null : aClass.getNavigationElement();
      if (aClass != navClass) return navClass;
    }
    return super.getGotoDeclarationTarget(element, navElement);
  }

  @Override
  public boolean includeSelfInGotoImplementation(final @NotNull PsiElement element) {
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

  private static PsiClass @Nullable [] getClassesWithMember(final PsiReference reference, final PsiMember member) {
    return ApplicationManager.getApplication().runReadAction(new Computable<>() {
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
          }
          else {
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

      private static PsiClass[] getInheritors(PsiClass containingClass, PsiClass psiClass, Set<? super PsiClass> visited) {
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
            return new PsiClass[]{psiClass};
          }
          psiClass = psiClass.getContainingClass();
        }
        return PsiClass.EMPTY_ARRAY;
      }
    });
  }


  @Override
  public @Nullable SearchScope getSearchScope(Editor editor, final @NotNull PsiElement element) {
    final PsiReferenceExpression referenceExpression = editor != null ? findReferenceExpression(editor) : null;
    if (referenceExpression != null && element instanceof PsiMethod) {
       if (!PsiUtil.canBeOverridden((PsiMethod)element)) {
         return element.getUseScope();
       }
      final PsiClass[] memberClass = getClassesWithMember(referenceExpression, (PsiMember)element);
      if (memberClass != null && memberClass.length == 1) {
        PsiClass aClass = memberClass[0];
        return CachedValuesManager.getCachedValue(
          aClass, () -> new CachedValueProvider.Result<>(
            getHierarchyScope(aClass, aClass.getUseScope(), true), PsiModificationTracker.MODIFICATION_COUNT));
      }
    }
    return super.getSearchScope(editor, element);
  }

  /**
   * Narrow given scope to include only those places where methods called on given class qualifier could be defined.
   *
   * @param aClass qualifier type class
   * @param scope a scope to narrow
   * @param areFunctionalInheritorsExpected true, iff scope should be ignored for functional interfaces to avoid eager functional expressions search
   * @return narrowed scope or null if <code>aClass</code> is a functional interface and functional expressions can be processed by the caller
   */
  @Contract("_,_,false->!null")
  public static @Nullable SearchScope getHierarchyScope(@NotNull PsiClass aClass,
                                                        @NotNull SearchScope scope,
                                                        boolean areFunctionalInheritorsExpected) {
    final List<PsiClass> classesToSearch = new ArrayList<>();
    classesToSearch.add(aClass);
    classesToSearch.addAll(ClassInheritorsSearch.search(aClass, scope, true).findAll());

    final Set<PsiClass> supers = new HashSet<>();
    for (PsiClass psiClass : classesToSearch) {
      if (areFunctionalInheritorsExpected && LambdaUtil.isFunctionalClass(psiClass)) {
        return null;
      }
      supers.addAll(InheritanceUtil.getSuperClasses(psiClass));
    }

    final List<PsiElement> elements = new ArrayList<>();
    elements.addAll(classesToSearch);
    elements.addAll(supers);

    return new LocalSearchScope(PsiUtilCore.toPsiElementArray(elements));
  }

  private static class PsiElementFindProcessor<T extends PsiClass> implements Processor<T> {
    private final T myElement;

    PsiElementFindProcessor(T t) {
      myElement = t;
    }

    @Override
    public boolean process(T t) {
      if (InheritanceUtil.isInheritorOrSelf(t, myElement, true)) return false;
      return !myElement.getManager().areElementsEquivalent(myElement, t);
    }
  }
}
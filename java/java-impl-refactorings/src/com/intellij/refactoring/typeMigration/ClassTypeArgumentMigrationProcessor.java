// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author anna
 */
public class ClassTypeArgumentMigrationProcessor {
  private static final Logger LOG = Logger.getInstance(ClassTypeArgumentMigrationProcessor.class);

  private final TypeMigrationLabeler myLabeler;

  public ClassTypeArgumentMigrationProcessor(final TypeMigrationLabeler labeler) {
    myLabeler = labeler;
  }

  public void migrateClassTypeParameter(final PsiReferenceParameterList referenceParameterList, final PsiClassType migrationType) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceParameterList, PsiClass.class);
    LOG.assertTrue(psiClass != null);

    final PsiClass superClass = psiClass.getSuperClass();
    LOG.assertTrue(superClass != null);

    myLabeler.getTypeEvaluator().setType(new TypeMigrationUsageInfo(superClass), migrationType);


    final Map<PsiElement, Pair<PsiReference[], PsiType>> roots = new HashMap<>();

    markTypeParameterUsages(psiClass, migrationType, referenceParameterList, roots);

    final Set<PsiElement> processed = new HashSet<>();
    for (Map.Entry<PsiElement, Pair<PsiReference[], PsiType>> entry : roots.entrySet()) {
      final PsiElement member = entry.getKey();
      final PsiType type = entry.getValue().second;

      if (member instanceof PsiParameter && ((PsiParameter)member).getDeclarationScope() instanceof PsiMethod) {
        myLabeler.migrateMethodCallExpressions(type, (PsiParameter)member, psiClass);
      }


      final PsiReference[] references = entry.getValue().first;
      for (PsiReference usage : references) {
        myLabeler.migrateRootUsageExpression(usage, processed);
      }
    }
  }

  private void markTypeParameterUsages(final PsiClass psiClass, PsiClassType migrationType, PsiReferenceParameterList referenceParameterList,
                                       final Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {

    final PsiSubstitutor[] fullHierarchySubstitutor = {migrationType.resolveGenerics().getSubstitutor()};
    CommonJavaRefactoringUtil.processSuperTypes(migrationType, new CommonJavaRefactoringUtil.SuperTypeVisitor() {
      @Override
      public void visitType(PsiType aType) {
        fullHierarchySubstitutor[0] = fullHierarchySubstitutor[0].putAll(((PsiClassType)aType).resolveGenerics().getSubstitutor());
      }
      @Override
      public void visitClass(PsiClass aClass) {
        //do nothing
      }
    });

    final PsiClass resolvedClass = (PsiClass)((PsiJavaCodeReferenceElement)referenceParameterList.getParent()).resolve();
    LOG.assertTrue(resolvedClass != null);
    final Set<PsiClass> superClasses = new HashSet<>();
    superClasses.add(resolvedClass);
    InheritanceUtil.getSuperClasses(resolvedClass, superClasses, true);
    for (PsiClass superSuperClass : superClasses) {
      final Set<PsiTypeParameter> typeParameters = ContainerUtil.newHashSet(PsiUtil.typeParametersIterable(superSuperClass));
      superSuperClass.accept(new JavaRecursiveElementVisitor(){
        @Override
        public void visitMethod(final @NotNull PsiMethod method) {
          super.visitMethod(method);
          processMemberType(method, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            processMemberType(parameter, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
          }
        }

        @Override
        public void visitField(final @NotNull PsiField field) {
          super.visitField(field);
          processMemberType(field, typeParameters, psiClass, fullHierarchySubstitutor[0], roots);
        }
      });
    }

  }

  private void processMemberType(final PsiElement element,
                                 final Set<PsiTypeParameter> typeParameters,
                                 final PsiClass psiClass,
                                 final PsiSubstitutor substitutor,
                                 final Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {
    final PsiType elementType = TypeMigrationLabeler.getElementType(element);
    if (elementType != null && PsiTypesUtil.mentionsTypeParameters(elementType, typeParameters)) {
      final PsiType memberType = substitutor.substitute(elementType);

      prepareMethodsChangeSignature(psiClass, element, memberType);

      final List<PsiReference> refs = TypeMigrationLabeler.filterReferences(psiClass, ReferencesSearch.search(element, psiClass.getUseScope()));

      roots.put(element, Pair.create(myLabeler.markRootUsages(element, memberType, refs.toArray(PsiReference.EMPTY_ARRAY)), memberType));
    }
  }

  /**
   * signature should be changed for methods with type parameters
   */
  private void prepareMethodsChangeSignature(final PsiClass currentClass, final PsiElement memberToChangeSignature, final PsiType memberType) {
    if (memberToChangeSignature instanceof PsiMethod superMethod) {
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, superMethod, true);
      if (method != null && method.getContainingClass() == currentClass) {
        myLabeler.addRoot(new TypeMigrationUsageInfo(method), memberType, method, false);
      }
    } else if (memberToChangeSignature instanceof PsiParameter superParameter &&
               superParameter.getDeclarationScope() instanceof PsiMethod superMethod) {
      final int parameterIndex = superMethod.getParameterList().getParameterIndex(superParameter);
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, superMethod, true);
      if (method != null && method.getContainingClass() == currentClass) {
        final PsiParameter parameter = Objects.requireNonNull(method.getParameterList().getParameter(parameterIndex));
        if (!parameter.getType().equals(memberType)) {
          myLabeler.addRoot(new TypeMigrationUsageInfo(parameter), memberType, parameter, false);
        }
      }
    }
  }
}

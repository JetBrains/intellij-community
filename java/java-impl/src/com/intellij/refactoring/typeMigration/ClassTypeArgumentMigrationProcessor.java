/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * @author anna
 * Date: 19-Apr-2008
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
    RefactoringHierarchyUtil.processSuperTypes(migrationType, new RefactoringHierarchyUtil.SuperTypeVisitor() {
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
      final TypeParameterSearcher parameterSearcher = new TypeParameterSearcher(superSuperClass.getTypeParameters());
      superSuperClass.accept(new JavaRecursiveElementVisitor(){
        @Override
        public void visitMethod(final PsiMethod method) {
          super.visitMethod(method);
          processMemberType(method, parameterSearcher, psiClass, fullHierarchySubstitutor[0], roots);
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            processMemberType(parameter, parameterSearcher, psiClass, fullHierarchySubstitutor[0], roots);
          }
        }

        @Override
        public void visitField(final PsiField field) {
          super.visitField(field);
          processMemberType(field, parameterSearcher, psiClass, fullHierarchySubstitutor[0], roots);
        }
      });
    }

  }

  private void processMemberType(final PsiElement element,
                                 final TypeParameterSearcher parameterSearcher,
                                 final PsiClass psiClass,
                                 final PsiSubstitutor substitutor,
                                 final Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {
    final PsiType elementType = TypeMigrationLabeler.getElementType(element);
    if (elementType != null && elementType.accept(parameterSearcher).booleanValue()) {
      final PsiType memberType = substitutor.substitute(elementType);

      prepareMethodsChangeSignature(psiClass, element, memberType);

      final List<PsiReference> refs = TypeMigrationLabeler.filterReferences(psiClass, ReferencesSearch.search(element, psiClass.getUseScope()));

      roots.put(element, Pair.create(myLabeler.markRootUsages(element, memberType, refs.toArray(new PsiReference[refs.size()])), memberType));
    }
  }

  /**
   * signature should be changed for methods with type parameters
   */
  private void prepareMethodsChangeSignature(final PsiClass currentClass, final PsiElement memberToChangeSignature, final PsiType memberType) {
    if (memberToChangeSignature instanceof PsiMethod) {
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, (PsiMethod)memberToChangeSignature, true);
      if (method != null && method.getContainingClass() == currentClass) {
        myLabeler.addRoot(new TypeMigrationUsageInfo(method), memberType, method, false);
      }
    } else if (memberToChangeSignature instanceof PsiParameter && ((PsiParameter)memberToChangeSignature).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod superMethod = (PsiMethod)((PsiParameter)memberToChangeSignature).getDeclarationScope();
      final int parameterIndex = superMethod.getParameterList().getParameterIndex((PsiParameter)memberToChangeSignature);
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, superMethod, true);
      if (method != null && method.getContainingClass() == currentClass) {
        final PsiParameter parameter = method.getParameterList().getParameters()[parameterIndex];
        if (!parameter.getType().equals(memberType)) {
          myLabeler.addRoot(new TypeMigrationUsageInfo(parameter), memberType, parameter, false);
        }
      }
    }
  }

  private static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

    private TypeParameterSearcher(final PsiTypeParameter[] set) {
      ContainerUtil.addAll(myTypeParams, set);
    }

    public Boolean visitType(final PsiType type) {
      return false;
    }

    public Boolean visitArrayType(final PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    public Boolean visitClassType(final PsiClassType classType) {
      final PsiClass aClass = classType.resolve();
      if (aClass instanceof PsiTypeParameter && myTypeParams.contains((PsiTypeParameter)aClass)) return true;

      final PsiType[] types = classType.getParameters();
      for (final PsiType psiType : types) {
        if (psiType.accept(this).booleanValue()) return true;
      }
      return false;
    }

    public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      return bound != null && bound.accept(this).booleanValue();
    }
  }

}

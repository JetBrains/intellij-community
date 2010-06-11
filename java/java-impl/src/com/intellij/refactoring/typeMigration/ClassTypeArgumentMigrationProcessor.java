/*
 * User: anna
 * Date: 19-Apr-2008
 */
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;

import java.util.*;

public class ClassTypeArgumentMigrationProcessor {
  private static final Logger LOG = Logger.getInstance("#" + ClassTypeArgumentMigrationProcessor.class.getName());

  private final TypeMigrationLabeler myLabeler;

  public ClassTypeArgumentMigrationProcessor(final TypeMigrationLabeler labeler) {
    myLabeler = labeler;
  }

  public void migrateClassTypeParameter(final PsiReferenceParameterList referenceParameterList, final PsiType migrationType) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceParameterList, PsiClass.class);
    LOG.assertTrue(psiClass != null);

    final PsiClass superClass = psiClass.getSuperClass();
    LOG.assertTrue(superClass != null);

    myLabeler.getTypeEvaluator().setType(new TypeMigrationUsageInfo(superClass), migrationType);


    final Map<PsiElement, Pair<PsiReference[], PsiType>> roots = new HashMap<PsiElement, Pair<PsiReference[], PsiType>>();

    markTypeParameterUsages(psiClass, migrationType, referenceParameterList, roots);

    final Set<PsiElement> processed = new HashSet<PsiElement>();
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

  private void markTypeParameterUsages(final PsiClass psiClass, PsiType migrationType, PsiReferenceParameterList referenceParameterList,
                                       final Map<PsiElement, Pair<PsiReference[], PsiType>> roots) {

    final Map<PsiClass, PsiTypeParameter[]> visibleTypeParams = getTypeParametersHierarchy(referenceParameterList);
    final PsiSubstitutor substitutor = composeSubstitutor(psiClass.getProject(), migrationType, visibleTypeParams);
    for (Map.Entry<PsiClass, PsiTypeParameter[]> entry : visibleTypeParams.entrySet()) {
      final TypeParameterSearcher parameterSearcher = new TypeParameterSearcher(entry.getValue());
      entry.getKey().accept(new JavaRecursiveElementVisitor(){
        @Override
        public void visitMethod(final PsiMethod method) {
          super.visitMethod(method);
          processMemberType(method, parameterSearcher, psiClass, substitutor, roots);
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            processMemberType(parameter, parameterSearcher, psiClass, substitutor, roots);
          }
        }

        @Override
        public void visitField(final PsiField field) {
          super.visitField(field);
          processMemberType(field, parameterSearcher, psiClass, substitutor, roots);
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

  private static PsiSubstitutor composeSubstitutor(final Project project, final PsiType migrationType, final Map<PsiClass, PsiTypeParameter[]> visibleTypeParams) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final PsiResolveHelper psiResolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    for (Map.Entry<PsiClass,PsiTypeParameter[]> entry : visibleTypeParams.entrySet()) {
      final PsiClassType clearedOriginalType = JavaPsiFacade.getElementFactory(project).createType(entry.getKey(), PsiSubstitutor.EMPTY);
      for (PsiTypeParameter parameter : entry.getValue()) {
        substitutor = substitutor.put(parameter,
                                      psiResolveHelper.getSubstitutionForTypeParameter(parameter, clearedOriginalType, migrationType, true, clearedOriginalType.getLanguageLevel()));
      }
    }
    return substitutor;
  }

  private static Map<PsiClass, PsiTypeParameter[]> getTypeParametersHierarchy(final PsiReferenceParameterList referenceParameterList) {
    final PsiElement parent = referenceParameterList.getParent();
    LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement);
    final PsiClass superClass = (PsiClass)((PsiJavaCodeReferenceElement)parent).resolve();
    LOG.assertTrue(superClass != null);

    final Map<PsiClass, PsiTypeParameter[]> visibleTypeParams = new HashMap<PsiClass, PsiTypeParameter[]>();
    visibleTypeParams.put(superClass, superClass.getTypeParameters());

    final HashSet<PsiClass> superClasses = new HashSet<PsiClass>();
    InheritanceUtil.getSuperClasses(superClass, superClasses, true);
    for (PsiClass superSuperClass : superClasses) {
      visibleTypeParams.put(superSuperClass, superSuperClass.getTypeParameters());
    }
    return visibleTypeParams;
  }

  /**
   * signature should be changed for methods with type parameters
   */
  private void prepareMethodsChangeSignature(final PsiClass currentClass, final PsiElement memberToChangeSignature, final PsiType memberType) {
    if (memberToChangeSignature instanceof PsiMethod) {
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, (PsiMethod)memberToChangeSignature, true);
      if (method.getContainingClass() == currentClass) {
        myLabeler.addRoot(new TypeMigrationUsageInfo(method), memberType, method, false);
      }
    } else if (memberToChangeSignature instanceof PsiParameter && ((PsiParameter)memberToChangeSignature).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod superMethod = (PsiMethod)((PsiParameter)memberToChangeSignature).getDeclarationScope();
      final int parameterIndex = superMethod.getParameterList().getParameterIndex((PsiParameter)memberToChangeSignature);
      final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(currentClass, superMethod, true);
      if (method.getContainingClass() == currentClass) {
        final PsiParameter parameter = method.getParameterList().getParameters()[parameterIndex];
        myLabeler.addRoot(new TypeMigrationUsageInfo(parameter), memberType, parameter, false);
      }
    }
  }

  private static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<PsiTypeParameter>();

    private TypeParameterSearcher(final PsiTypeParameter[] set) {
      myTypeParams.addAll(Arrays.asList(set));
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

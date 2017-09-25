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
package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.*;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class JUnitUtil {
  @NonNls public static final String TESTCASE_CLASS = "junit.framework.TestCase";
  @NonNls private static final String TEST_INTERFACE = "junit.framework.Test";
  @NonNls private static final String TESTSUITE_CLASS = "junit.framework.TestSuite";
  @NonNls public static final String TEST_ANNOTATION = "org.junit.Test";
  @NonNls public static final String TEST5_PACKAGE_FQN = "org.junit.jupiter.api";
  @NonNls public static final String TEST5_ANNOTATION = "org.junit.jupiter.api.Test";
  @NonNls public static final String CUSTOM_TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable";
  @NonNls public static final String TEST5_FACTORY_ANNOTATION = "org.junit.jupiter.api.TestFactory";
  @NonNls public static final String IGNORE_ANNOTATION = "org.junit.Ignore";
  @NonNls public static final String RUN_WITH = "org.junit.runner.RunWith";
  @NonNls public static final String DATA_POINT = "org.junit.experimental.theories.DataPoint";
  @NonNls public static final String SUITE_METHOD_NAME = "suite";

  public static final String BEFORE_ANNOTATION_NAME = "org.junit.Before";
  public static final String AFTER_ANNOTATION_NAME = "org.junit.After";

  public static final String BEFORE_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeEach";
  public static final String AFTER_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.AfterEach";

  public static final String PARAMETRIZED_PARAMETERS_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameters";
  public static final String PARAMETRIZED_PARAMETER_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameter";

  public static final String AFTER_CLASS_ANNOTATION_NAME = "org.junit.AfterClass";
  public static final String BEFORE_CLASS_ANNOTATION_NAME = "org.junit.BeforeClass";
  public static final Collection<String> TEST5_CONFIG_METHODS = Collections.unmodifiableList(Arrays.asList(BEFORE_EACH_ANNOTATION_NAME,
                                                                                                           AFTER_EACH_ANNOTATION_NAME));

  public static final String BEFORE_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeAll";
  public static final String AFTER_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.AfterAll";
  public static final Collection<String> TEST5_STATIC_CONFIG_METHODS = Collections.unmodifiableList(Arrays.asList(BEFORE_ALL_ANNOTATION_NAME,
                                                                                                                  AFTER_ALL_ANNOTATION_NAME));

  private static final Collection<String> TEST_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(TEST_ANNOTATION,
                                                                                                        TEST5_ANNOTATION,
                                                                                                        TEST5_FACTORY_ANNOTATION));
  public static final Collection<String> TEST5_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(TEST5_ANNOTATION,
                                                                                                        TEST5_FACTORY_ANNOTATION,
                                                                                                        CUSTOM_TESTABLE_ANNOTATION));
  public static final Collection<String> TEST5_JUPITER_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(TEST5_ANNOTATION,
                                                                                                        TEST5_FACTORY_ANNOTATION));

  private static final List<String> INSTANCE_CONFIGS = Arrays.asList(BEFORE_ANNOTATION_NAME, AFTER_ANNOTATION_NAME);
  private static final List<String> INSTANCE_5_CONFIGS = Arrays.asList(BEFORE_EACH_ANNOTATION_NAME, AFTER_EACH_ANNOTATION_NAME);

  private static final List<String> STATIC_5_CONFIGS = Arrays.asList(BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME);

  private static final List<String> STATIC_CONFIGS = Arrays.asList(BEFORE_CLASS_ANNOTATION_NAME, AFTER_CLASS_ANNOTATION_NAME,
                                                                   PARAMETRIZED_PARAMETERS_ANNOTATION_NAME);
  private static final Collection<String> CONFIGURATIONS_ANNOTATION_NAME = Collections.unmodifiableList(
    Arrays.asList(DATA_POINT, AFTER_ANNOTATION_NAME, BEFORE_ANNOTATION_NAME, AFTER_CLASS_ANNOTATION_NAME, BEFORE_CLASS_ANNOTATION_NAME,
                  BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME));
  
  @NonNls public static final String PARAMETERIZED_CLASS_NAME = "org.junit.runners.Parameterized";
  @NonNls public static final String SUITE_CLASS_NAME = "org.junit.runners.Suite";
  public static final String JUNIT5_NESTED = "org.junit.jupiter.api.Nested";

  public static boolean isSuiteMethod(@NotNull PsiMethod psiMethod) {
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (psiMethod.isConstructor()) return false;
    if (psiMethod.getParameterList().getParametersCount() > 0) return false;
    final PsiType returnType = psiMethod.getReturnType();
    if (returnType == null || returnType instanceof PsiPrimitiveType) return false;
    return returnType.equalsToText(TEST_INTERFACE)||
           returnType.equalsToText(TESTSUITE_CLASS) ||
           InheritanceUtil.isInheritor(returnType, TEST_INTERFACE);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location) {
    return isTestMethod(location, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract) {
    return isTestMethod(location, checkAbstract, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith) {
    return isTestMethod(location, checkAbstract, checkRunWith, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith, boolean checkClass) {
    final PsiMethod psiMethod = location.getPsiElement();
    final PsiClass aClass = location instanceof MethodLocation ? ((MethodLocation)location).getContainingClass() : psiMethod.getContainingClass();
    if (checkClass && (aClass == null || !isTestClass(aClass, checkAbstract, true))) return false;
    if (isTestAnnotated(psiMethod)) return true;
    if (psiMethod.isConstructor()) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (AnnotationUtil.isAnnotated(psiMethod, CONFIGURATIONS_ANNOTATION_NAME, false)) return false;
    if (checkClass && checkRunWith) {
      PsiAnnotation annotation = getRunWithAnnotation(aClass);
      if (annotation != null) {
        return !isParameterized(annotation);
      }
    }
    if (psiMethod.getParameterList().getParametersCount() > 0) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!psiMethod.getName().startsWith("test")) return false;
    if (checkClass) {
      PsiClass testCaseClass = getTestCaseClassOrNull(location);
      if (testCaseClass == null || !psiMethod.getContainingClass().isInheritor(testCaseClass, true)) return false;
    }
    return PsiType.VOID.equals(psiMethod.getReturnType());
  }

  public static boolean isTestCaseInheritor(final PsiClass aClass) {
    if (!aClass.isValid()) return false;
    Location<PsiClass> location = PsiLocation.fromPsiElement(aClass);
    PsiClass testCaseClass = getTestCaseClassOrNull(location);
    return testCaseClass != null && aClass.isInheritor(testCaseClass, true);
  }

  public static boolean isTestClass(final PsiClass psiClass) {
    return isTestClass(psiClass, true, true);
  }

  public static boolean isTestClass(@NotNull PsiClass psiClass, boolean checkAbstract, boolean checkForTestCaseInheritance) {
    if (psiClass.getQualifiedName() == null) return false;
    if (isJUnit5(psiClass) && isJUnit5TestClass(psiClass, checkAbstract)) {
      return true;
    }
    final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(psiClass, PsiClass.class);
    if (topLevelClass != null) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(topLevelClass, Collections.singleton(RUN_WITH));
      if (annotation != null) {
        final PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue("value");
        if (attributeValue instanceof PsiClassObjectAccessExpression) {
          final String runnerName = ((PsiClassObjectAccessExpression)attributeValue).getOperand().getType().getCanonicalText();
          if (!(PARAMETERIZED_CLASS_NAME.equals(runnerName) || SUITE_CLASS_NAME.equals(runnerName))) {
            return true;
          }
        }
      }
    }
    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) return false;

    if (AnnotationUtil.isAnnotated(psiClass, RUN_WITH, true)) return true;

    if (checkForTestCaseInheritance && isTestCaseInheritor(psiClass)) return true;

    return CachedValuesManager.getCachedValue(psiClass, () ->
      CachedValueProvider.Result.create(hasTestOrSuiteMethods(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  private static boolean hasTestOrSuiteMethods(@NotNull PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (isSuiteMethod(method)) return true;
      if (isTestAnnotated(method)) return true;
    }

    if (isJUnit5(psiClass)) {
      for (PsiClass innerClass : psiClass.getInnerClasses()) {
        for (PsiMethod method : innerClass.getAllMethods()) {
          if (isTestAnnotated(method)) return true;
        }
      }
    }

    return false;
  }

  public static boolean isJUnit3TestClass(final PsiClass clazz) {
    return isTestCaseInheritor(clazz);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass) {
    return isJUnit4TestClass(psiClass, true);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return false;
    final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(modifierList, PsiClass.class);
    if (topLevelClass != null && AnnotationUtil.isAnnotated(topLevelClass, RUN_WITH, true)) return true;

    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) return false;

    for (final PsiMethod method : psiClass.getAllMethods()) {
      ProgressManager.checkCanceled();
      if (isTestAnnotated(method)) return true;
    }

    return false;
  }

  public static boolean isJUnit5TestClass(@NotNull final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return false;

    if (psiClass.getContainingClass() != null && AnnotationUtil.isAnnotated(psiClass, JUNIT5_NESTED, false)) {
      return true;
    }

    if (MetaAnnotationUtil.isMetaAnnotated(psiClass, Collections.singleton(CUSTOM_TESTABLE_ANNOTATION))) {
      return true;
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, false, checkAbstract)) return false;

    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module != null) {
      return CachedValuesManager.getCachedValue(psiClass, () -> {
        boolean hasAnnotation = false;
        for (final PsiMethod method : psiClass.getAllMethods()) {
          ProgressManager.checkCanceled();
          if (MetaAnnotationUtil.isMetaAnnotated(method, TEST5_ANNOTATIONS)) {
            hasAnnotation = true;
            break;
          }
        }

        if (!hasAnnotation) {
          for (PsiClass aClass : psiClass.getAllInnerClasses()) {
            if (MetaAnnotationUtil.isMetaAnnotated(aClass, Collections.singleton(JUNIT5_NESTED))) {
              hasAnnotation = true;
              break;
            }
          }
        }
        return CachedValueProvider.Result.create(hasAnnotation, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      });
    }

    return false;
  }

  public static boolean isJUnit5(@NotNull PsiElement element) {
    return isJUnit5(element.getResolveScope(), element.getProject());
  }

  public static boolean isJUnit5(GlobalSearchScope scope, Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    Condition<String> foundCondition = aPackageName -> {
      PsiPackage aPackage = facade.findPackage(aPackageName);
      return aPackage != null && aPackage.getDirectories(scope).length > 0;
    };

    return ReadAction.compute(() -> foundCondition.value(TEST5_PACKAGE_FQN));
  }
  
  public static boolean isTestAnnotated(final PsiMethod method) {
    if (AnnotationUtil.isAnnotated(method, TEST_ANNOTATION, false) || JUnitRecognizer.willBeAnnotatedAfterCompilation(method)) {
      return true;
    }

    return MetaAnnotationUtil.isMetaAnnotated(method, TEST5_ANNOTATIONS);
  }


  @Nullable
  private static PsiClass getTestCaseClassOrNull(final Location<?> location) {
    final Location<PsiClass> ancestorOrSelf = location.getAncestorOrSelf(PsiClass.class);
    if (ancestorOrSelf == null) return null;
    final PsiClass aClass = ancestorOrSelf.getPsiElement();
    Module module = JavaExecutionUtil.findModule(aClass);
    if (module == null) return null;
    GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClassOrNull(scope, module.getProject());
  }

  public static PsiClass getTestCaseClass(final Module module) throws NoJUnitException {
    if (module == null) throw new NoJUnitException();
    final GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClass(scope, module.getProject());
  }

  public static PsiClass getTestCaseClass(final SourceScope scope) throws NoJUnitException {
    if (scope == null) throw new NoJUnitException();
    return getTestCaseClass(scope.getLibrariesScope(), scope.getProject());
  }

  public static void checkTestCase(SourceScope scope, Project project) throws NoJUnitException {
    if (scope == null) throw new NoJUnitException();
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("junit.framework");
    if (aPackage == null || aPackage.getDirectories(scope.getLibrariesScope()).length == 0) {
      throw new NoJUnitException();
    }
  }

  private static PsiClass getTestCaseClass(final GlobalSearchScope scope, final Project project) throws NoJUnitException {
    PsiClass testCaseClass = getTestCaseClassOrNull(scope, project);
    if (testCaseClass == null) throw new NoJUnitException(scope.getDisplayName());
    return testCaseClass;
  }

  @Nullable
  private static PsiClass getTestCaseClassOrNull(final GlobalSearchScope scope, final Project project) {
    return JavaPsiFacade.getInstance(project).findClass(TESTCASE_CLASS, scope);
  }

  public static boolean isTestMethodOrConfig(@NotNull PsiMethod psiMethod) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    if (isTestMethod(PsiLocation.fromPsiElement(psiMethod), false)) {
      assert containingClass != null : psiMethod + "; " + psiMethod.getClass() + "; " + psiMethod.getParent();
      if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final boolean[] foundNonAbstractInheritor = new boolean[1];
        ClassInheritorsSearch.search(containingClass).forEach(psiClass -> {
          if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            foundNonAbstractInheritor[0] = true;
            return false;
          }
          return true;
        });
        if (foundNonAbstractInheritor[0]) {
          return true;
        }
      } else {
        return true;
      }
    }
    final String name = psiMethod.getName();
    final boolean isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC);
    if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (isPublic && (SUITE_METHOD_NAME.equals(name) || "setUp".equals(name) || "tearDown".equals(name))) {
        return true;
      }

      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_CONFIGS)) {
          return isPublic;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS)) {
          return true;
        }
      }
      else {
        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_CONFIGS)) {
          return isPublic;
        }

        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_5_CONFIGS)) {
          return true;
        }
        
        if (TestUtils.testInstancePerClass(containingClass) && AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static PsiMethod findFirstTestMethod(PsiClass clazz) {
    PsiMethod testMethod = null;
    for (PsiMethod method : clazz.getMethods()) {
      if (isTestMethod(MethodLocation.elementInClass(method, clazz)) || isSuiteMethod(method)) {
        testMethod = method;
        break;
      }
    }
    return testMethod;
  }

  @Nullable
  public static PsiMethod findSuiteMethod(PsiClass clazz) {
    final PsiMethod[] suiteMethods = clazz.findMethodsByName(SUITE_METHOD_NAME, false);
    for (PsiMethod method : suiteMethods) {
      if (isSuiteMethod(method)) return method;
    }
    return null;
  }

  public static PsiAnnotation getRunWithAnnotation(PsiClass aClass) {
    return AnnotationUtil.findAnnotationInHierarchy(aClass, Collections.singleton(RUN_WITH));
  }

  public static boolean isParameterized(PsiAnnotation annotation) {
    final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    if (value instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression)value).getOperand();
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
      return psiClass != null && "org.junit.runners.Parameterized".equals(psiClass.getQualifiedName());
    }
    return false;
  }

  public static class  TestMethodFilter implements Condition<PsiMethod> {
    private final PsiClass myClass;
    private final JavaTestFramework framework;

    public TestMethodFilter(final PsiClass aClass) {
      myClass = aClass;
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      this.framework = (framework instanceof JavaTestFramework) ? (JavaTestFramework)framework : null;
    }

    public boolean value(final PsiMethod method) {
      return framework != null
             ? framework.isTestMethod(method, myClass)
             : isTestMethod(MethodLocation.elementInClass(method, myClass));
    }
  }

  public static PsiClass findPsiClass(final String qualifiedName, final Module module, final Project project) {
    final GlobalSearchScope scope = module == null ? GlobalSearchScope.projectScope(project) : GlobalSearchScope.moduleWithDependenciesScope(module);
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
  }

  public static PsiPackage getContainingPackage(@NotNull PsiClass psiClass) {
    PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    return directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
  }

  public static PsiClass getTestClass(final PsiElement element) {
    return getTestClass(PsiLocation.fromPsiElement(element));
  }

  public static PsiClass getTestClass(final Location<?> location) {
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false); iterator.hasNext();) {
      final Location<PsiClass> classLocation = iterator.next();
      if (isTestClass(classLocation.getPsiElement(), false, true)) return classLocation.getPsiElement();
    }
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length == 1 && isTestClass(classes[0], false, true)) return classes[0];
    }
    return null;
  }

  public static PsiMethod getTestMethod(final PsiElement element) {
    return getTestMethod(element, true);
  }


  public static PsiMethod getTestMethod(final PsiElement element, boolean checkAbstract) {
    return getTestMethod(element, checkAbstract, true);
  }

  public static PsiMethod getTestMethod(final PsiElement element, boolean checkAbstract, boolean checkRunWith) {
    final PsiManager manager = element.getManager();
    final Location<PsiElement> location = PsiLocation.fromPsiElement(manager.getProject(), element);
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<? extends PsiMethod> methodLocation = iterator.next();
      if (isTestMethod(methodLocation, checkAbstract, checkRunWith)) return methodLocation.getPsiElement();
    }
    return null;
  }

  public static class NoJUnitException extends CantRunException {
    public NoJUnitException() {
      super(ExecutionBundle.message("no.junit.error.message"));
    }

    public NoJUnitException(final String message) {
      super(ExecutionBundle.message("no.junit.in.scope.error.message", message));
    }
  }
}

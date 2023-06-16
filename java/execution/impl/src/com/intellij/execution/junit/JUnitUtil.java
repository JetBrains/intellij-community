// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public final class JUnitUtil {
  public static final String TEST_CASE_CLASS = "junit.framework.TestCase";
  private static final String TEST_INTERFACE = "junit.framework.Test";
  private static final String TEST_SUITE_CLASS = "junit.framework.TestSuite";
  public static final String TEST_ANNOTATION = "org.junit.Test";
  public static final String RULE_ANNOTATION = "org.junit.Rule";
  public static final String TEST5_PACKAGE_FQN = "org.junit.jupiter.api";
  public static final String TEST5_ANNOTATION = "org.junit.jupiter.api.Test";
  
  public static final String CUSTOM_TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable";
  public static final Set<String> CUSTOM_TESTABLE_ANNOTATION_LIST = Collections.singleton(CUSTOM_TESTABLE_ANNOTATION);
  
  public static final String TEST5_FACTORY_ANNOTATION = "org.junit.jupiter.api.TestFactory";
  public static final String RUN_WITH = "org.junit.runner.RunWith";
  public static final String DATA_POINT = "org.junit.experimental.theories.DataPoint";
  public static final String SUITE_METHOD_NAME = "suite";

  public static final String BEFORE_ANNOTATION_NAME = "org.junit.Before";
  public static final String AFTER_ANNOTATION_NAME = "org.junit.After";

  public static final String BEFORE_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeEach";
  public static final String AFTER_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.AfterEach";

  public static final String PARAMETRIZED_PARAMETERS_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameters";
  public static final String PARAMETRIZED_PARAMETER_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameter";

  public static final String AFTER_CLASS_ANNOTATION_NAME = "org.junit.AfterClass";
  public static final String BEFORE_CLASS_ANNOTATION_NAME = "org.junit.BeforeClass";
  public static final String BEFORE_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeAll";
  public static final String AFTER_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.AfterAll";

  public static final Collection<String> TEST5_JUPITER_ANNOTATIONS =
    List.of(TEST5_ANNOTATION, TEST5_FACTORY_ANNOTATION);

  private static final List<String> INSTANCE_CONFIGS = Arrays.asList(BEFORE_ANNOTATION_NAME, AFTER_ANNOTATION_NAME);
  private static final List<String> INSTANCE_5_CONFIGS = Arrays.asList(BEFORE_EACH_ANNOTATION_NAME, AFTER_EACH_ANNOTATION_NAME);

  private static final List<String> STATIC_CONFIGS = Arrays.asList(
    BEFORE_CLASS_ANNOTATION_NAME, AFTER_CLASS_ANNOTATION_NAME, PARAMETRIZED_PARAMETERS_ANNOTATION_NAME);
  private static final List<String> STATIC_5_CONFIGS = Arrays.asList(BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME);

  private static final Collection<String> CONFIGURATIONS_ANNOTATION_NAME =
    List.of(DATA_POINT, AFTER_ANNOTATION_NAME, BEFORE_ANNOTATION_NAME, AFTER_CLASS_ANNOTATION_NAME, BEFORE_CLASS_ANNOTATION_NAME,
            BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME, RULE_ANNOTATION);

  public static final String PARAMETERIZED_CLASS_NAME = "org.junit.runners.Parameterized";
  public static final String SUITE_CLASS_NAME = "org.junit.runners.Suite";
  public static final String JUNIT5_NESTED = "org.junit.jupiter.api.Nested";

  private static final String[] RUNNERS_UNAWARE_OF_INNER_CLASSES = {
    "org.junit.runners.Parameterized",
    "org.junit.runners.BlockJUnit4ClassRunner",
    "org.junit.runners.JUnit4",
    "org.junit.internal.runners.JUnit38ClassRunner",
    "org.junit.internal.runners.JUnit4ClassRunner",
    "org.junit.runners.Suite"
  };

  private static final String[] RUNNERS_REQUIRE_ANNOTATION_ON_TEST_METHOD = {
    "org.junit.runners.Parameterized",
    "org.junit.runners.BlockJUnit4ClassRunner",
    "org.junit.runners.JUnit4",
    "org.junit.internal.runners.JUnit4ClassRunner",
    "org.mockito.junit.MockitoJUnitRunner",
    "org.mockito.junit.MockitoJUnitRunner.StrictStubs",
    "org.mockito.junit.MockitoJUnitRunner.Silent",
    "org.mockito.junit.MockitoJUnitRunner.Strict"
  };

  public static boolean isSuiteMethod(@NotNull PsiMethod psiMethod) {
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (psiMethod.isConstructor()) return false;
    if (!psiMethod.getParameterList().isEmpty()) return false;
    final PsiType returnType = psiMethod.getReturnType();
    if (returnType == null || returnType instanceof PsiPrimitiveType) return false;
    return returnType.equalsToText(TEST_INTERFACE) ||
           returnType.equalsToText(TEST_SUITE_CLASS) ||
           InheritanceUtil.isInheritor(returnType, TEST_INTERFACE);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location) {
    return isTestMethod(location, true);
  }

  public static boolean isTestMethod(@NotNull final Location<? extends PsiMethod> location, boolean checkAbstract) {
    return isTestMethod(location, checkAbstract, true);
  }

  public static boolean isTestMethod(@NotNull final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith) {
    return isTestMethod(location, checkAbstract, checkRunWith, true);
  }

  public static boolean isTestMethod(@NotNull final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith, boolean checkClass) {
    final PsiMethod psiMethod = location.getPsiElement();
    final PsiClass aClass = location instanceof MethodLocation ? ((MethodLocation)location).getContainingClass() : psiMethod.getContainingClass();
    if (checkClass && (aClass == null || !isTestClass(aClass, checkAbstract, true))) return false;
    if (isTestAnnotated(psiMethod, true)) return !psiMethod.hasModifierProperty(PsiModifier.STATIC);
    if (aClass != null && MetaAnnotationUtil.isMetaAnnotatedInHierarchy(aClass, Collections.singletonList(CUSTOM_TESTABLE_ANNOTATION))) return true;
    if (psiMethod.isConstructor()) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (AnnotationUtil.isAnnotated(psiMethod, CONFIGURATIONS_ANNOTATION_NAME, 0)) return false;
    if (checkClass && checkRunWith) {
      PsiAnnotation annotation = getRunWithAnnotation(aClass);
      if (annotation != null) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null ||
            CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          return false;
        }
        return !isOneOf(annotation, RUNNERS_REQUIRE_ANNOTATION_ON_TEST_METHOD);
      }
    }
    if (!psiMethod.getParameterList().isEmpty()) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!psiMethod.getName().startsWith("test")) return false;
    if (checkClass) {
      PsiClass testCaseClass = getTestCaseClassOrNull(aClass);
      if (psiMethod.getContainingClass() == null) return false;
      if (testCaseClass == null || !psiMethod.getContainingClass().isInheritor(testCaseClass, true)) return false;
    }
    return PsiTypes.voidType().equals(psiMethod.getReturnType());
  }

  public static boolean isTestCaseInheritor(final PsiClass aClass) {
    if (!aClass.isValid()) return false;
    PsiClass testCaseClass = getTestCaseClassOrNull(aClass);
    return testCaseClass != null && aClass.isInheritor(testCaseClass, true);
  }

  public static boolean isTestClass(@NotNull final PsiClass psiClass) {
    return isTestClass(psiClass, true, true);
  }

  private static boolean hasTestableMetaAnnotation(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getInstance(psiClass.getProject())
          .findClass(CUSTOM_TESTABLE_ANNOTATION, psiClass.getResolveScope()) != null &&
           MetaAnnotationUtil.hasMetaAnnotatedMethods(psiClass, CUSTOM_TESTABLE_ANNOTATION_LIST);
  }

  public static boolean isTestClass(@NotNull PsiClass psiClass, boolean checkAbstract, boolean checkForTestCaseInheritance) {
    if (psiClass.getQualifiedName() == null) return false;
    if (isJUnit5(psiClass)) {
      if (isJUnit5TestClass(psiClass, checkAbstract)) {
        return true;
      }
    }
    else if (JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CUSTOM_TESTABLE_ANNOTATION, psiClass.getResolveScope()) != null && 
             MetaAnnotationUtil.isMetaAnnotatedInHierarchy(psiClass, CUSTOM_TESTABLE_ANNOTATION_LIST) ||
             hasTestableMetaAnnotation(psiClass)) {
      //no jupiter engine in the classpath
      return true;
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) return false;

    final PsiClass topLevelClass = getTopmostClass(psiClass);
    if (topLevelClass != null) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(topLevelClass, Collections.singleton(RUN_WITH));
      if (annotation != null) {
        if (topLevelClass == psiClass) {
          return true;
        }

        if (!isInheritorOrSelfRunner(annotation, RUNNERS_UNAWARE_OF_INNER_CLASSES)) {
          return true;
        }
      }
    }

    if (AnnotationUtil.isAnnotated(psiClass, RUN_WITH, CHECK_HIERARCHY)) return true;

    if (checkForTestCaseInheritance && isTestCaseInheritor(psiClass)) return true;

    return CachedValuesManager.getCachedValue(psiClass, () ->
      CachedValueProvider.Result.create(hasTestOrSuiteMethods(psiClass), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean hasTestOrSuiteMethods(@NotNull PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (isSuiteMethod(method)) return true;
      if (isExplicitlyTestAnnotated(method)) {
        return true;
      }
    }

    PsiClass[] classes = psiClass.getInnerClasses();
    if (classes.length > 0 && isJUnit5(psiClass)) {
      for (PsiClass innerClass : classes) {
        for (PsiMethod method : innerClass.getAllMethods()) {
          if (isExplicitlyTestAnnotated(method)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isJUnit3TestClass(final PsiClass clazz) {
    return PsiClassUtil.isRunnableClass(clazz, true, false) && isTestCaseInheritor(clazz);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass) {
    return isJUnit4TestClass(psiClass, true);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return false;
    if (psiClass.getQualifiedName() == null) return false; //skip local and anonymous classes
    if (JavaPsiFacade.getInstance(psiClass.getProject())
          .findClass(TEST_ANNOTATION, psiClass.getResolveScope()) == null) {
      return false;
    }
    PsiClass topLevelClass = getTopmostClass(psiClass);

    if (topLevelClass != null) {
      if (AnnotationUtil.isAnnotated(topLevelClass, RUN_WITH, CHECK_HIERARCHY)) {
        PsiAnnotation annotation = getRunWithAnnotation(topLevelClass);
        if (topLevelClass == psiClass) {
          return true;
        }

        if (AnnotationUtil.isAnnotated(psiClass, RUN_WITH, CHECK_HIERARCHY)) {
          return true;
        }

        //default runners do not implicitly run inner classes
        if (annotation != null && !isInheritorOrSelfRunner(annotation, RUNNERS_UNAWARE_OF_INNER_CLASSES)) {
          return true;
        }
      }
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) return false;

    for (final PsiMethod method : psiClass.getAllMethods()) {
      ProgressManager.checkCanceled();
      if (TestUtils.isExplicitlyJUnit4TestAnnotated(method) || JUnitRecognizer.willBeAnnotatedAfterCompilation(method)) return true;
    }

    return false;
  }

  private static PsiClass getTopmostClass(PsiClass psiClass) {
    PsiClass topLevelClass = psiClass;
    while (topLevelClass != null && topLevelClass.getContainingClass() != null) {
      topLevelClass = topLevelClass.getContainingClass();
    }
    return topLevelClass;
  }

  public static boolean isJUnit5TestClass(@NotNull final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return false;

    if (psiClass.isAnnotationType()) return false;

    if (psiClass.getContainingClass() != null && 
        !psiClass.hasModifierProperty(PsiModifier.PRIVATE) &&
        !psiClass.hasModifierProperty(PsiModifier.STATIC) &&
        MetaAnnotationUtil.isMetaAnnotated(psiClass, Collections.singleton(JUNIT5_NESTED))) {
      return true;
    }

    if (MetaAnnotationUtil.isMetaAnnotatedInHierarchy(psiClass, CUSTOM_TESTABLE_ANNOTATION_LIST)) {
      return true;
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, false, checkAbstract)) return false;

    return CachedValuesManager.getCachedValue(psiClass, () -> {
      boolean hasAnnotation = AnnotationUtil.isAnnotated(psiClass, "org.junit.jupiter.api.extension.ExtendWith", 0);
      if (!hasAnnotation) {
        for (final PsiMethod method : psiClass.getAllMethods()) {
          ProgressManager.checkCanceled();
          if (!method.hasModifierProperty(PsiModifier.PRIVATE) &&
              !method.hasModifierProperty(PsiModifier.STATIC) &&
              MetaAnnotationUtil.isMetaAnnotated(method, CUSTOM_TESTABLE_ANNOTATION_LIST)) {
            hasAnnotation = true;
            break;
          }
        }
      }

      if (!hasAnnotation) {
        for (PsiClass aClass : psiClass.getAllInnerClasses()) {
          if (!aClass.hasModifierProperty(PsiModifier.PRIVATE) &&
              !aClass.hasModifierProperty(PsiModifier.STATIC) &&
              MetaAnnotationUtil.isMetaAnnotated(aClass, Collections.singleton(JUNIT5_NESTED))) {
            hasAnnotation = true;
            break;
          }
        }
      }
      return CachedValueProvider.Result.create(hasAnnotation, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  public static boolean isJUnit5(@NotNull PsiElement element) {
    return isJUnit5(element.getResolveScope(), element.getProject());
  }

  public static boolean isJUnit5(GlobalSearchScope scope, Project project) {
    return hasPackageWithDirectories(JavaPsiFacade.getInstance(project), TEST5_PACKAGE_FQN, scope);
  }

  public static boolean hasPackageWithDirectories(JavaPsiFacade facade, String packageQName, GlobalSearchScope globalSearchScope) {
    return ReadAction.nonBlocking(() -> {
      PsiPackage aPackage = facade.findPackage(packageQName);
      return aPackage != null && aPackage.getDirectories(globalSearchScope).length > 0;
    }).executeSynchronously();
  }

  public static boolean isTestAnnotated(final PsiMethod method) {
    return isTestAnnotated(method, true);
  }

  public static boolean isTestAnnotated(final PsiMethod method, boolean includeCustom) {
    if (isJUnit4TestAnnotated(method)) {
      return true;
    }

    return MetaAnnotationUtil.isMetaAnnotated(method, includeCustom ? CUSTOM_TESTABLE_ANNOTATION_LIST : TEST5_JUPITER_ANNOTATIONS);
  }

  private static boolean isExplicitlyTestAnnotated(PsiMethod method) {
    return TestUtils.isExplicitlyJUnit4TestAnnotated(method) ||
           JUnitRecognizer.willBeAnnotatedAfterCompilation(method) || MetaAnnotationUtil.isMetaAnnotated(method, CUSTOM_TESTABLE_ANNOTATION_LIST);
  }

  public static boolean isJUnit4TestAnnotated(PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, TEST_ANNOTATION, CHECK_HIERARCHY) || JUnitRecognizer.willBeAnnotatedAfterCompilation(method);
  }

  @Nullable
  private static PsiClass getTestCaseClassOrNull(final PsiClass psiClass) {
    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null) return null;
    GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClassOrNull(scope, module.getProject());
  }

  @NotNull
  public static PsiClass getTestCaseClass(final Module module) throws NoJUnitException {
    if (module == null) throw new NoJUnitException();
    final GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClass(scope, module.getProject());
  }

  @NotNull
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

  @NotNull
  private static PsiClass getTestCaseClass(@NotNull final GlobalSearchScope scope, @NotNull final Project project) throws NoJUnitException {
    PsiClass testCaseClass = getTestCaseClassOrNull(scope, project);
    if (testCaseClass == null) throw new NoJUnitException(scope.getDisplayName());
    return testCaseClass;
  }

  @Nullable
  private static PsiClass getTestCaseClassOrNull(@NotNull final GlobalSearchScope scope, @NotNull final Project project) {
    return JavaPsiFacade.getInstance(project).findClass(TEST_CASE_CLASS, scope);
  }

  public static boolean isTestMethodOrConfig(@NotNull PsiMethod psiMethod) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    if (isTestMethod(PsiLocation.fromPsiElement(psiMethod), false)) {
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
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_CONFIGS, 0)) {
          return isPublic;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS, 0)) {
          return true;
        }
      }
      else {
        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_CONFIGS, 0)) {
          return isPublic;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_5_CONFIGS, 0)) {
          return true;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS, 0) && TestUtils.testInstancePerClass(containingClass)) {
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
    return isOneOf(annotation, "org.junit.runners.Parameterized");
  }

  public static boolean isInheritorOrSelfRunner(PsiAnnotation annotation, String... runners) {
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    final PsiAnnotationMemberValue value = attribute != null ? attribute.getDetachedValue() : null;
    if (value instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression)value).getOperand();
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
      return psiClass != null && ContainerUtil.exists(runners, runner -> InheritanceUtil.isInheritor(psiClass, runner));
    }
    return false;
  }

  public static boolean isOneOf(PsiAnnotation annotation, String... runners) {
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    final PsiAnnotationMemberValue value = attribute != null ? attribute.getDetachedValue() : null;
    if (value instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression)value).getOperand();
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
      if (psiClass != null) {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null && ArrayUtil.find(runners, qualifiedName) > -1) return true;
      }
    }
    return false;
  }

  public static class  TestMethodFilter implements Condition<PsiMethod> {
    private final PsiClass myClass;
    private final TestFramework framework;

    public TestMethodFilter(final PsiClass aClass) {
      myClass = aClass;
      this.framework = TestFrameworks.detectFramework(aClass);
    }

    @Override
    public boolean value(final PsiMethod method) {
      if (framework == null) {
        return false;
      }
      if (framework instanceof JavaTestFramework) {
        return ((JavaTestFramework)framework).isTestMethod(method, myClass);
      }
      return framework.isTestMethod(method);
    }
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
    if (element instanceof PsiMethod) {
      Location<PsiMethod> location = PsiLocation.fromPsiElement(manager.getProject(), (PsiMethod)element);
      return isTestMethod(location, checkAbstract, checkRunWith) ? (PsiMethod)element : null;
    }
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
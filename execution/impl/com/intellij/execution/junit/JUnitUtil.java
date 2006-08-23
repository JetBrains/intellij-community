package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.Graph;
import gnu.trove.THashSet;
import junit.runner.BaseTestRunner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JUnitUtil {
  @NonNls private static final String TESTCASE_CLASS = "junit.framework.TestCase";
  @NonNls private static final String TEST_INTERFACE = "junit.framework.Test";
  @NonNls private static final String TESTSUITE_CLASS = "junit.framework.TestSuite";

  public static boolean isSuiteMethod(final PsiMethod psiMethod) {
    if (psiMethod == null) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (psiMethod.isConstructor()) return false;
    final PsiType returnType = psiMethod.getReturnType();
    if (returnType != null && !returnType.equalsToText(TEST_INTERFACE) && !returnType.equalsToText(TESTSUITE_CLASS)) return false;
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    return parameters.length == 0;
  }

  public static interface FindCallback {
    /**
     * Invoked in dispatch thread
     */
    void found(@NotNull Collection<PsiClass> classes, final boolean isJunit4);
  }

  public static void findTestsWithProgress(final FindCallback callback, final TestClassFilter classFilter) {
    if (isSyncSearch()) {
      THashSet<PsiClass> classes = new THashSet<PsiClass>();
      boolean isJUnit4 = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      callback.found(classes, isJUnit4);
      return;
    }

    final THashSet<PsiClass> classes = new THashSet<PsiClass>();
    final boolean[] isJunit4 = new boolean[1];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        isJunit4[0] = ConfigurationUtil.findAllTestClasses(classFilter, classes);
      }
    }, ExecutionBundle.message("seaching.test.progress.title"), true, classFilter.getProject());

    callback.found(classes, isJunit4[0]);
  }

  private static boolean isSyncSearch() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location) {
    final Location<PsiClass> aClass = location.getParent(PsiClass.class);
    if (aClass == null) return false;
    if (!isTestClass(aClass)) return false;
    final PsiMethod psiMethod = location.getPsiElement();
    if (isTestAnnotated(psiMethod)) return true;
    if (psiMethod.isConstructor()) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (psiMethod.getParameterList().getParameters().length > 0) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC) && BaseTestRunner.SUITE_METHODNAME.equals(psiMethod.getName())) return false;
    final PsiClass testCaseClass;
    try {
      testCaseClass = getTestCaseClass(location);
    } catch (NoJUnitException e) {
      return false;
    }
    return psiMethod.getContainingClass().isInheritor(testCaseClass, true);
  }

  private static boolean isTestCaseInheritor(final PsiClass aClass) {
    try {
      if (!aClass.isValid()) return false;
      Location<PsiClass> location = PsiLocation.fromPsiElement(aClass);
      final PsiClass testCaseClass = getTestCaseClass(location);
      return aClass.isInheritor(testCaseClass, true);
    }
    catch (NoJUnitException e) {
      return false;
    }
  }

  /**
   *
   * @param aClassLocation
   * @return true iff aClassLocation can be used as JUnit test class.
   */
  public static boolean isTestClass(final Location<? extends PsiClass> aClassLocation) {
    return isTestClass(aClassLocation.getPsiElement());
  }

  public static boolean isTestClass(final PsiClass psiClass) {
    if (!ExecutionUtil.isRunnableClass(psiClass)) return false;
    if (isTestCaseInheritor(psiClass)) return true;

    for (final PsiMethod method : psiClass.getMethods()) {
      if (isSuiteMethod(method)) return true;
      if (isTestAnnotated(method)) return true;
    }
    return false;
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass) {
    if (!ExecutionUtil.isRunnableClass(psiClass)) return false;

    for (final PsiMethod method : psiClass.getMethods()) {
      if (isTestAnnotated(method)) return true;
    }
    return false;
  }

  public static boolean isTestAnnotated(final PsiMethod method) {
    return method.getModifierList().findAnnotation("org.junit.Test") != null;
  }

  private static PsiClass getTestCaseClass(final Location<?> location) throws NoJUnitException {
    final Location<PsiClass> ancestorOrSelf = location.getAncestorOrSelf(PsiClass.class);
    final PsiClass aClass = ancestorOrSelf.getPsiElement();
    Module module = aClass == null ? null : ExecutionUtil.findModule(aClass);
    return getTestCaseClass(module);
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

  private static PsiClass getTestCaseClass(final GlobalSearchScope scope, final Project project) throws NoJUnitException {
    final PsiClass testCaseClass = PsiManager.getInstance(project).findClass(TESTCASE_CLASS, scope); // TODO do not search in sources;
    if (testCaseClass == null) throw new NoJUnitException(scope.getDisplayName());
    return testCaseClass;
  }

  public static class  TestMethodFilter implements Condition<PsiMethod> {
    private final PsiClass myClass;

    public TestMethodFilter(final PsiClass aClass) {
      myClass = aClass;
    }

    public boolean value(final PsiMethod method) {
      return isTestMethod(MethodLocation.elementInClass(method, myClass));
    }
  }

  public static PsiClass findPsiClass(final String qualifiedName, final Module module, final Project project, final boolean searchInLibs) {
    final GlobalSearchScope scope;
    if (module != null) {
      scope = searchInLibs
              ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
              : GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    else {
      scope = searchInLibs ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    }
    return PsiManager.getInstance(project).findClass(qualifiedName, scope);
  }

  public static PsiPackage getContainingPackage(final PsiClass psiClass) {
    return psiClass.getContainingFile().getContainingDirectory().getPackage();
  }

  public static PsiClass getTestClass(final PsiElement element) {
    return JUnitConfigurationProducer.getTestClass(PsiLocation.fromPsiElement(element));
  }

  public static PsiMethod getTestMethod(final PsiElement element) {
    final PsiManager manager = element.getManager();
    final Location<PsiElement> location = PsiLocation.fromPsiElement(manager.getProject(), element);
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<? extends PsiMethod> methodLocation = iterator.next();
      if (isTestMethod(methodLocation)) return methodLocation.getPsiElement();
    }
    return null;
  }

  /**
   * @param collection
   * @param comparator returns 0 iff elemets are incomparable.
   * @return maximum elements
   */
  public static <T> Collection<T> findMaximums(final Collection<T> collection, final Comparator<T> comparator) {
    final ArrayList<T> maximums = new ArrayList<T>();
    loop:
    for (final T candidate : collection) {
      for (final T element : collection) {
        if (comparator.compare(element, candidate) > 0) continue loop;
      }
      maximums.add(candidate);
    }
    return maximums;
  }

  public static Map<Module, Collection<Module>> buildAllDependencies(final Project project) {
    Graph<Module> graph = ModuleManager.getInstance(project).moduleGraph();
    Map<Module, Collection<Module>> result = new HashMap<Module, Collection<Module>>();
    for (final Module module : graph.getNodes()) {
      buildDependenciesForModule(module, graph, result);
    }
    return result;
  }

  private static void buildDependenciesForModule(final Module module, final Graph<Module> graph, Map<Module, Collection<Module>> map) {
    final Set<Module> deps = new HashSet<Module>();
    map.put(module, deps);

    new Object() {
      void traverse(Module m) {
        for (Iterator<Module> iterator = graph.getIn(m); iterator.hasNext();) {
          final Module dep = iterator.next();
          if (!deps.contains(dep)) {
            deps.add(dep);
            traverse(dep);
          }
        }
      }
    }.traverse(module);
  }

  /*public static Map<Module, Collection<Module>> buildAllDependencies(final Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getSortedModules();
    final HashMap<Module, Collection<Module>> lessers = new HashMap<Module, Collection<Module>>();
    int prevProcessedCount = 0;
    while (modules.length > lessers.size()) {
      for (int i = 0; i < modules.length; i++) {
        final Module module = modules[i];
        if (lessers.containsKey(module)) continue;
        final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
        if (lessers.keySet().containsAll(Arrays.asList(dependencies))) {
          final HashSet<Module> allDependencies = new HashSet<Module>();
          for (int j = 0; j < dependencies.length; j++) {
            final Module dependency = dependencies[j];
            allDependencies.add(dependency);
            allDependencies.addAll(lessers.get(dependency));
          }
          lessers.put(module, allDependencies);
        }
      }
      if (lessers.size() == prevProcessedCount) return null;
      prevProcessedCount = lessers.size();
    }
    return lessers;
  }*/

  public static class ModuleOfClass implements Convertor<PsiClass, Module> {
    public Module convert(final PsiClass psiClass) {
      if (psiClass == null || !psiClass.isValid()) return null;
      return ModuleUtil.findModuleForPsiElement(psiClass);
    }
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

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author cdr
 */
public abstract class OrderEntryFix implements IntentionAction, LocalQuickFix {
  private static final String JUNIT4_LIBRARY_NAME = "JUnit4";

  OrderEntryFix() {
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  @Nullable
  public static List<LocalQuickFix> registerFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull final PsiReference reference) {
    final PsiElement psiElement = reference.getElement();
    @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;

    final VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return null;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return null;

    if ("TestCase".equals(referenceName) || isAnnotation(psiElement) && isJunitAnnotationName(referenceName, psiElement)) {
      final boolean isJunit4 = !referenceName.equals("TestCase");
      @NonNls final String className = isJunit4 ? "org.junit." + referenceName : "junit.framework.TestCase";
      PsiClass found =
        JavaPsiFacade.getInstance(project).findClass(className, currentModule.getModuleWithDependenciesAndLibrariesScope(true));
      if (found != null) return null; //no need to add junit to classpath
      final OrderEntryFix fix = new OrderEntryFix() {
        @Override
        @NotNull
        public String getText() {
          return QuickFixBundle.message("orderEntry.fix.add.junit.jar.to.classpath");
        }

        @Override
        @NotNull
        public String getFamilyName() {
          return getText();
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return !project.isDisposed() && !currentModule.isDisposed();
        }

        @Override
        public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
          List<String> jarPaths;
          String libraryName;
          if (isJunit4) {
            jarPaths = getJUnit4JarPaths();
            libraryName = JUNIT4_LIBRARY_NAME;
          }
          else {
            jarPaths = Collections.singletonList(JavaSdkUtil.getJunit3JarPath());
            libraryName = null;
          }
          addJarsToRootsAndImportClass(jarPaths, libraryName, currentModule, editor, reference, className);
        }
      };
      registrar.register(fix);
      return Collections.singletonList((LocalQuickFix)fix);
    }

    if (isAnnotation(psiElement) && AnnotationUtil.isJetbrainsAnnotation(referenceName)) {
      @NonNls final String className = "org.jetbrains.annotations." + referenceName;
      PsiClass found =
        JavaPsiFacade.getInstance(project).findClass(className, currentModule.getModuleWithDependenciesAndLibrariesScope(true));
      if (found != null) return null; //no need to add junit to classpath
      final OrderEntryFix fix = new OrderEntryFix() {
        @Override
        @NotNull
        public String getText() {
          return QuickFixBundle.message("orderEntry.fix.add.annotations.jar.to.classpath");
        }

        @Override
        @NotNull
        public String getFamilyName() {
          return getText();
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return !project.isDisposed() && !currentModule.isDisposed();
        }

        @Override
        public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final String libraryPath = locateAnnotationsJar(currentModule);
              if (libraryPath != null) {
                new WriteCommandAction(project) {
                  @Override
                  protected void run(final Result result) throws Throwable {
                    addJarsToRootsAndImportClass(Collections.singletonList(libraryPath), null, currentModule, editor, reference,
                                                 "org.jetbrains.annotations." + referenceName);
                  }
                }.execute();
              }
            }
          });
        }
      };
      registrar.register(fix);
      return Collections.singletonList((LocalQuickFix)fix);
    }

    List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    Set<Object> librariesToAdd = new THashSet<Object>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(psiElement.getProject());
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);
    if (allowedDependencies.isEmpty()) {
      return result;
    }
    classes = allowedDependencies.toArray(new PsiClass[allowedDependencies.size()]);
    final OrderEntryFix moduleDependencyFix = new AddModuleDependencyFix(currentModule, classVFile, classes, reference);
    registrar.register(moduleDependencyFix);
    result.add(moduleDependencyFix);
    for (final PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(currentModule).getFileIndex();
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          final Library library = libraryEntry.getLibrary();
          if (library == null) continue;
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];

          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library)) continue;
          OrderEntry entryForFile = moduleFileIndex.getOrderEntryForFile(virtualFile);
          if (entryForFile != null &&
              !(entryForFile instanceof ExportableOrderEntry &&
                ((ExportableOrderEntry)entryForFile).getScope() == DependencyScope.TEST &&
                !ModuleRootManager.getInstance(currentModule).getFileIndex().isInTestSourceContent(classVFile))) {
            continue;
          }
          final OrderEntryFix fix = new OrderEntryFix() {
            @Override
            @NotNull
            public String getText() {
              return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", libraryEntry.getPresentableName());
            }

            @Override
            @NotNull
            public String getFamilyName() {
              return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
            }

            @Override
            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
              return !project.isDisposed() && !currentModule.isDisposed() && libraryEntry.isValid();
            }

            @Override
            public void invoke(@NotNull final Project project, @Nullable final Editor editor, PsiFile file) {
              OrderEntryUtil.addLibraryToRoots(libraryEntry, currentModule);
              if (editor != null) {
                DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
                  @Override
                  public void run() {
                    new AddImportAction(project, reference, editor, aClass).execute();
                  }
                });
              }
            }
          };
          registrar.register(fix);
          result.add(fix);
        }
      }
    }
    return result;
  }

  public static void addJUnit4Library(boolean inTests, Module currentModule) throws ClassNotFoundException {
    final List<String> junit4Paths = getJUnit4JarPaths();
    addJarsToRoots(junit4Paths, JUNIT4_LIBRARY_NAME, currentModule, null);
  }

  @NotNull
  private static List<String> getJUnit4JarPaths() {
    try {
      return Arrays.asList(JavaSdkUtil.getJunit4JarPath(),
                           PathUtil.getJarPathForClass(Class.forName("org.hamcrest.Matcher")),
                           PathUtil.getJarPathForClass(Class.forName("org.hamcrest.Matchers")));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<PsiClass> filterAllowedDependencies(PsiElement element, PsiClass[] classes) {
    DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(element.getProject());
    PsiFile fromFile = element.getContainingFile();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (dependencyValidationManager.getViolatorDependencyRule(fromFile, psiClass.getContainingFile()) == null) {
        result.add(psiClass);
      }
    }
    return result;
  }

  private static boolean isAnnotation(final PsiElement psiElement) {
    return PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class) != null && PsiUtil.isLanguageLevel5OrHigher(psiElement);
  }

  private static boolean isJunitAnnotationName(@NonNls final String referenceName, @NotNull final PsiElement psiElement) {
    if ("Test".equals(referenceName) || "Ignore".equals(referenceName) || "RunWith".equals(referenceName) ||
        "Before".equals(referenceName) || "BeforeClass".equals(referenceName) ||
        "After".equals(referenceName) || "AfterClass".equals(referenceName)) {
      return true;
    }
    final PsiElement parent = psiElement.getParent();
    if (parent != null && !(parent instanceof PsiAnnotation)) {
      final PsiReference reference = parent.getReference();
      if (reference != null) {
        final String referenceText = parent.getText();
        if (isJunitAnnotationName(reference.getRangeInElement().substring(referenceText), parent)) {
          final int lastDot = referenceText.lastIndexOf('.');
          return lastDot > -1 && referenceText.substring(0, lastDot).equals("org.junit");
        }
      }
    }
    return false;
  }

  /**
   * @deprecated use {@link #addJarsToRootsAndImportClass} instead
   */
  public static void addBundledJarToRoots(final Project project, @Nullable final Editor editor, final Module currentModule,
                                          @Nullable final PsiReference reference,
                                          @NonNls final String className,
                                          @NonNls final String libVirtFile) {
    addJarsToRootsAndImportClass(Collections.singletonList(libVirtFile), null, currentModule, editor, reference, className);
  }

  public static void addJarsToRootsAndImportClass(@NotNull List<String> jarPaths,
                                                  final String libraryName,
                                                  @NotNull final Module currentModule, @Nullable final Editor editor,
                                                  @Nullable final PsiReference reference,
                                                  @NonNls final String className) {
    addJarsToRoots(jarPaths, libraryName, currentModule, reference != null ? reference.getElement() : null);

    final Project project = currentModule.getProject();
    if (editor != null && reference != null && className != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
        @Override
        public void run() {
          GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
          PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
          if (aClass != null) {
            new AddImportAction(project, reference, editor, aClass).execute();
          }
        }
      });
    }
  }

  public static void addJarToRoots(@NotNull String jarPath, final @NotNull Module module, @Nullable PsiElement location) {
    addJarsToRoots(Collections.singletonList(jarPath), null, module, location);
  }

  public static void addJarsToRoots(@NotNull List<String> jarPaths, @Nullable String libraryName,
                                    @NotNull Module module, @Nullable PsiElement location) {
    List<String> urls = ContainerUtil.map(jarPaths, new Function<String, String>() {
      @Override
      public String fun(String path) {
        return refreshAndConvertToUrl(path);
      }
    });
    boolean inTests = false;
    if (location != null) {
      final VirtualFile vFile = location.getContainingFile().getVirtualFile();
      if (vFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vFile)) {
        inTests = true;
      }
    }
    ModuleRootModificationUtil.addModuleLibrary(module, libraryName, urls, Collections.<String>emptyList(),
                                                inTests ? DependencyScope.TEST : DependencyScope.COMPILE);
  }

  @NotNull
  private static String refreshAndConvertToUrl(String jarPath) {
    final File libraryRoot = new File(jarPath);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libraryRoot);
    return VfsUtil.getUrlForLibraryRoot(libraryRoot);
  }

  public static boolean ensureAnnotationsJarInPath(final Module module) {
    if (isAnnotationsJarInPath(module)) return true;
    if (module == null) return false;
    final String libraryPath = locateAnnotationsJar(module);
    if (libraryPath != null) {
      new WriteCommandAction(module.getProject()) {
        @Override
        protected void run(final Result result) throws Throwable {
          addJarToRoots(libraryPath, module, null);
        }
      }.execute();
      return true;
    }
    return false;
  }

  @Nullable
  public static String locateAnnotationsJar(@NotNull Module module) {
    String jarName;
    String libPath;
    if (EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module).isAtLeast(LanguageLevel.JDK_1_8)) {
      jarName = "annotations-java8.jar";
      libPath = new File(PathManager.getHomePath(), "redist").getAbsolutePath();
    }
    else {
      jarName = "annotations.jar";
      libPath = PathManager.getLibPath();
    }
    final LocateLibraryDialog dialog = new LocateLibraryDialog(module, libPath, jarName, QuickFixBundle.message("add.library.annotations.description"));
    return dialog.showAndGet() ? dialog.getResultingLibraryPath() : null;
  }

  public static boolean isAnnotationsJarInPath(Module module) {
    if (module == null) return false;
    return JavaPsiFacade.getInstance(module.getProject())
             .findClass(AnnotationUtil.LANGUAGE, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.compiler.ModuleCompilerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public abstract class OrderEntryFix implements IntentionAction, LocalQuickFix {
  private OrderEntryFix() {
  }

  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  public String getName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    try {
      invoke(project, null, descriptor.getPsiElement().getContainingFile());
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static List<LocalQuickFix> registerFixes(@NotNull QuickFixActionRegistrar registrar, final PsiReference reference) {
    final PsiElement psiElement = reference.getElement();
    @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;

    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return null;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return null;

    if ("TestCase".equals(referenceName) || isAnnotation(psiElement) && isJunitAnnotationName(referenceName)) {
      final boolean isJunit4 = !referenceName.equals("TestCase");
      @NonNls final String className = isJunit4 ? "org.junit." + referenceName : "junit.framework.TestCase";
      PsiClass found =
        JavaPsiFacade.getInstance(project).findClass(className, currentModule.getModuleWithDependenciesAndLibrariesScope(true));
      if (found != null) return null; //no need to add junit to classpath
      final OrderEntryFix fix = new OrderEntryFix() {
        @NotNull
        public String getText() {
          return QuickFixBundle.message("orderEntry.fix.add.junit.jar.to.classpath");
        }

        @NotNull
        public String getFamilyName() {
          return getText();
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return !project.isDisposed() && !currentModule.isDisposed();
        }

        public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
          String jarPath = isJunit4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
          addBundledJarToRoots(project, editor, currentModule, reference, className, jarPath);
        }
      };
      registrar.register(fix);
      return Arrays.asList((LocalQuickFix)fix);
    }

    if (isAnnotation(psiElement) && AnnotationUtil.isJetbrainsAnnotation(referenceName)) {
      @NonNls final String className = "org.jetbrains.annotations." + referenceName;
      PsiClass found =
        JavaPsiFacade.getInstance(project).findClass(className, currentModule.getModuleWithDependenciesAndLibrariesScope(true));
      if (found != null) return null; //no need to add junit to classpath
      final OrderEntryFix fix = new OrderEntryFix() {
        @NotNull
        public String getText() {
          return QuickFixBundle.message("orderEntry.fix.add.annotations.jar.to.classpath");
        }

        @NotNull
        public String getFamilyName() {
          return getText();
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return !project.isDisposed() && !currentModule.isDisposed();
        }

        public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final LocateLibraryDialog dialog = new LocateLibraryDialog(currentModule, PathManager.getLibPath(), "annotations.jar",
                                                                   QuickFixBundle.message("add.library.annotations.description"));
              dialog.show();
              if (dialog.isOK()) {
                new WriteCommandAction(project) {
                  protected void run(final Result result) throws Throwable {
                    addBundledJarToRoots(project, editor, currentModule, reference, "org.jetbrains.annotations." + referenceName,
                                         dialog.getResultingLibraryPath());
                  }
                }.execute();
              }
            }
          });
        }
      };
      registrar.register(fix);
      return Arrays.asList((LocalQuickFix)fix);
    }

    List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    Set<Object> librariesToAdd = new THashSet<Object>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(psiElement.getProject());
    PsiClass[] classes = facade.getShortNamesCache().getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    for (final PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        final OrderEntryFix fix = new OrderEntryFix() {
          @NotNull
          public String getText() {
            return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module", classModule.getName());
          }

          @NotNull
          public String getFamilyName() {
            return QuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
          }

          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return !project.isDisposed() && !classModule.isDisposed() && !currentModule.isDisposed();
          }

          public void invoke(@NotNull final Project project, @Nullable final Editor editor, PsiFile file) {
            final Runnable doit = new Runnable() {
              public void run() {
                ModifiableRootModel model = ModuleRootManager.getInstance(currentModule).getModifiableModel();
                model.addModuleOrderEntry(classModule);
                model.commit();
                if (editor != null) {
                  new AddImportAction(project, reference, editor, aClass).execute();
                }
              }
            };
            final Pair<Module, Module> circularModules = ModuleCompilerUtil.addingDependencyFormsCircularity(currentModule, classModule);
            if (circularModules == null) {
              doit.run();
            }
            else {
              showCircularWarningAndContinue(project, circularModules, classModule, doit);
            }
          }
        };
        registrar.register(fix);
        result.add(fix);
      }
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(currentModule).getFileIndex();
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          final Library library = libraryEntry.getLibrary();
          if (library == null) continue;
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];

          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library) || moduleFileIndex.getOrderEntryForFile(virtualFile) != null) continue;
          final OrderEntryFix fix = new OrderEntryFix() {
            @NotNull
            public String getText() {
              return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", libraryEntry.getPresentableName());
            }

            @NotNull
            public String getFamilyName() {
              return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
            }

            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
              return !project.isDisposed() && !currentModule.isDisposed() && libraryEntry.isValid();
            }

            public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
              OrderEntryUtil.addLibraryToRoots(libraryEntry, currentModule);
              if (editor != null) {
                new AddImportAction(project, reference, editor, aClass).execute();
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

  private static boolean isAnnotation(final PsiElement psiElement) {
    return psiElement.getParent() instanceof PsiAnnotation && PsiUtil.isLanguageLevel5OrHigher(psiElement);
  }

  private static boolean isJunitAnnotationName(@NonNls final String referenceName) {
    return "Test".equals(referenceName) || "Ignore".equals(referenceName) || "RunWith".equals(referenceName) ||
           "Before".equals(referenceName) || "BeforeClass".equals(referenceName) ||
           "After".equals(referenceName) || "AfterClass".equals(referenceName);

  }

  public static void addBundledJarToRoots(final Project project,
                                          @Nullable final Editor editor,
                                          final Module currentModule,
                                          @Nullable final PsiReference reference,
                                          @NonNls final String className,
                                          @NonNls final String libVirtFile) {
    addJarToRoots(libVirtFile, currentModule, reference != null ? reference.getElement() : null);

    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
    if (aClass != null && editor != null && reference != null) {
      new AddImportAction(project, reference, editor, aClass).execute();
    }
  }

  public static void addJarToRoots(String libPath, final Module module, @Nullable PsiElement location) {
    String url = VfsUtil.getUrlForLibraryRoot(new File(libPath));
    VirtualFile libVirtFile = VirtualFileManager.getInstance().findFileByUrl(url);
    assert libVirtFile != null : libPath;

    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = manager.getModifiableModel();
    final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
    final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
    libraryModel.addRoot(libVirtFile, OrderRootType.CLASSES);
    libraryModel.commit();

    if (location != null) {
      final VirtualFile vFile = location.getContainingFile().getVirtualFile();
      if (vFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vFile)) {
        final LibraryOrderEntry orderEntry = rootModel.findLibraryOrderEntry(jarLibrary);
        orderEntry.setScope(DependencyScope.TEST);
      }
    }

    rootModel.commit();
  }

  private static void showCircularWarningAndContinue(final Project project, final Pair<Module, Module> circularModules,
                                                     final Module classModule,
                                                     final Runnable doit) {
    final String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning", classModule.getName(),
                                                     circularModules.getFirst().getName(), circularModules.getSecond().getName());
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    ApplicationManager.getApplication().invokeLater(new Runnable(){
      public void run() {
        if (!project.isOpen()) return;
        int ret = Messages.showOkCancelDialog(project, message,
                                              QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning"),
                                              Messages.getWarningIcon());
        if (ret == 0) {
          ApplicationManager.getApplication().runWriteAction(doit);
        }
      }
    });
  }

  public static boolean ensureAnnotationsJarInPath(final Module module, String annotationName) {
    if (module == null) return false;
    final PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject())
      .findClass(annotationName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    if (psiClass != null) return true;
    final LocateLibraryDialog dialog = new LocateLibraryDialog(
      module, PathManager.getLibPath(), "annotations.jar",
      QuickFixBundle.message("add.library.annotations.description"));
    dialog.show();
    if (dialog.isOK()) {
      new WriteCommandAction(module.getProject()) {
        protected void run(final Result result) throws Throwable {
          addJarToRoots(dialog.getResultingLibraryPath(), module, null);
        }
      }.execute();
      return true;
    }
    return false;
  }
}

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

/**
 * @author cdr
 */
public abstract class OrderEntryFix implements IntentionAction {
  private OrderEntryFix() {
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void registerFixes(HighlightInfo info, final PsiJavaCodeReferenceElement reference) {
    @NonNls final String referenceName = reference.getReferenceName();
    if (referenceName == null) return;

    Project project = reference.getProject();
    PsiFile containingFile = reference.getContainingFile();
    if (containingFile == null) return;

    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;

    if ("TestCase".equals(referenceName) || isAnnotation(reference) && isJunitAnnotationName(referenceName)) {
      QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
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

        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          boolean isJunit4 = ! referenceName.equals("TestCase");
          String jarPath = isJunit4 ? PathUtilEx.getJunit4JarPath() : PathUtilEx.getJunit3JarPath();
          addBundledJarToRoots(project, editor, currentModule, reference,
                               isJunit4 ? "org.junit." + referenceName : "junit.framework.TestCase",
                               jarPath);
        }
      });
      return;
    }

    if (isAnnotation(reference) && AnnotationUtil.isJetbrainsAnnotation(referenceName)) {
      QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
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

        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          LocateLibraryDialog dialog = new LocateLibraryDialog (currentModule, PathManager.getLibPath(), "annotations.jar", 
                                                                QuickFixBundle.message("add.library.annotations.description"));
          dialog.show();
          if (dialog.isOK()) {
            addBundledJarToRoots(project, editor, currentModule, reference, "org.jetbrains.annotations." + referenceName,
                                 dialog.getResultingLibraryPath());
          }
        }
      });
      return;
    }

    Set<Object> librariesToAdd = new THashSet<Object>();
    PsiClass[] classes = reference.getManager().getShortNamesCache().getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    for (final PsiClass aClass : classes) {
      if (!aClass.getManager().getResolveHelper().isAccessible(aClass, reference, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
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

          public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
            final Runnable doit = new Runnable() {
              public void run() {
                ModifiableRootModel model = ModuleRootManager.getInstance(currentModule).getModifiableModel();
                model.addModuleOrderEntry(classModule);
                model.commit();
                new AddImportAction(project, reference, editor, aClass).execute();
              }
            };
            final Pair<Module,Module> circularModules = ModulesConfigurator.addingDependencyFormsCircularity(currentModule, classModule);
            if (circularModules == null) {
              doit.run();
            }
            else {
              showCircularWarningAndContinue(project, circularModules, classModule, doit);
            }
          }
        });
      }
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          final Library library = libraryEntry.getLibrary();
          if (library == null) continue;
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];
          
          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library)) continue;
          QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
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

            public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
              OrderEntryUtil.addLibraryToRoots(libraryEntry, currentModule);
              new AddImportAction(project, reference, editor, aClass).execute();
            }
          });
        }
      }
    }
  }

  private static boolean isAnnotation(final PsiJavaCodeReferenceElement reference) {
    return reference.getParent() instanceof PsiAnnotation && PsiUtil.getLanguageLevel(reference).compareTo(LanguageLevel.JDK_1_5) >= 0;
  }

  private static boolean isJunitAnnotationName(@NonNls final String referenceName) {
    return "Test".equals(referenceName) || "Ignore".equals(referenceName) || "RunWith".equals(referenceName) ||
           "Before".equals(referenceName) || "BeforeClass".equals(referenceName) ||
           "After".equals(referenceName) || "AfterClass".equals(referenceName);

  }

  public static void addBundledJarToRoots(final Project project, final Editor editor, final Module currentModule, final PsiJavaCodeReferenceElement reference,
                                           @NonNls final String className,
                                           @NonNls final String libPath) {
    String url = VfsUtil.getUrlForLibraryRoot(new File(libPath));
    VirtualFile libVirtFile = VirtualFileManager.getInstance().findFileByUrl(url);
    assert libVirtFile != null : libPath;

    addJarToRoots(libVirtFile, currentModule);

    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
    PsiClass aClass = PsiManager.getInstance(project).findClass(className, scope);
    if ( aClass != null ) {
      new AddImportAction(project, reference, editor, aClass).execute();
    }
  }

  private static void addJarToRoots(VirtualFile jarFile, final Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = manager.getModifiableModel();
    final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
    final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
    libraryModel.addRoot(jarFile, OrderRootType.CLASSES);
    libraryModel.commit();
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
}

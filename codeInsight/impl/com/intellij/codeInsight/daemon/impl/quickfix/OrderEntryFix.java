package com.intellij.codeInsight.daemon.impl.quickfix;

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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

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
    String name = reference.getReferenceName();
    if (name == null) return;
    Project project = reference.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    PsiFile containingFile = reference.getContainingFile();
    if (containingFile == null) return;
    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;
    Set<VirtualFile> librariesToAdd = new THashSet<VirtualFile>();
    PsiClass[] classes = reference.getManager().getShortNamesCache().getClassesByName(name, GlobalSearchScope.allScope(project));
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

          public boolean isAvailable(Project project, Editor editor, PsiFile file) {
            return !project.isDisposed() && !classModule.isDisposed() && !currentModule.isDisposed();
          }

          public void invoke(final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
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
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];

          if (jar == null || !librariesToAdd.add(jar)) continue;
          QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
            @NotNull
            public String getText() {
              return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", libraryEntry.getPresentableName());
            }

            @NotNull
            public String getFamilyName() {
              return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
            }

            public boolean isAvailable(Project project, Editor editor, PsiFile file) {
              return !project.isDisposed() && !currentModule.isDisposed() && libraryEntry.isValid();
            }

            public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
              addJarToRoots(jar, currentModule);
              new AddImportAction(project, reference, editor, aClass).execute();
            }
          });
        }
      }
    }
    if (classes.length == 0) {
      @NonNls final String referenceName = reference.getReferenceName();
      if ("TestCase".equals(referenceName)
          || reference.getParent() instanceof PsiAnnotation && "Test".equals(referenceName) && PsiUtil.getLanguageLevel(reference).compareTo(LanguageLevel.JDK_1_5) >= 0
        ) {
        QuickFixAction.registerQuickFixAction(info, new OrderEntryFix(){
          @NotNull
          public String getText() {
            return QuickFixBundle.message("orderEntry.fix.add.junit.jar.to.classpath");
          }

          @NotNull
          public String getFamilyName() {
            return getText();
          }

          public boolean isAvailable(Project project, Editor editor, PsiFile file) {
            return !project.isDisposed() && !currentModule.isDisposed();
          }

          public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            boolean isJunit4 = referenceName.equals("Test");
            @NonNls String junitPath = PathManager.getLibPath() + (isJunit4 ? "/junit-4.0.jar" : "/junit.jar");
            String url = VfsUtil.getUrlForLibraryRoot(new File(junitPath));
            VirtualFile junit = VirtualFileManager.getInstance().findFileByUrl(url);
            assert junit != null : junitPath;

            addJarToRoots(junit, currentModule);

            GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
            String className = isJunit4 ? "org.junit.Test" : "junit.framework.TestCase";
            PsiClass aClass = PsiManager.getInstance(project).findClass(className, scope);
            new AddImportAction(project, reference, editor, aClass).execute();
          }
        });
      }
    }
  }

  protected static void addJarToRoots(final VirtualFile jarFile, final Module module) {
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

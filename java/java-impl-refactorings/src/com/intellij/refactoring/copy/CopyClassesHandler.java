// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.copy;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.SkipOverwriteChoice;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CopyClassesHandler extends CopyHandlerDelegateBase {
  private static final Logger LOG = Logger.getInstance(CopyClassesHandler.class);

  @Override
  public boolean forbidToClone(PsiElement[] elements, boolean fromUpdate) {
    final Map<PsiFile, PsiClass[]> fileMap = convertToTopLevelClasses(elements, fromUpdate, null, null);
    if (fileMap != null && fileMap.size() == 1) {
      final PsiClass[] psiClasses = fileMap.values().iterator().next();
      return psiClasses != null && psiClasses.length > 1;
    }
    return true;
  }

  @Override
  public boolean canCopy(PsiElement[] elements, boolean fromUpdate) {
    return canCopyClass(fromUpdate, elements);
  }

  @NotNull
  @Override
  public String getActionName(PsiElement[] elements) {
    if (elements.length == 1 && !(elements[0] instanceof PsiPackage) && !(elements[0] instanceof PsiDirectory)) {
      return JavaRefactoringBundle.message("copy.handler.copy.class.with.dialog");
    }
    return JavaRefactoringBundle.message("copy.handler.copy.classes.with.dialog");
  }

  public static boolean canCopyClass(PsiElement... elements) {
    return canCopyClass(false, elements);
  }

  public static boolean canCopyClass(boolean fromUpdate, PsiElement... elements) {
    if (fromUpdate && elements.length > 0 && elements[0] instanceof PsiDirectory) return true;
    return convertToTopLevelClasses(elements, fromUpdate, null, null) != null;
  }

  @Nullable
  private static Map<PsiFile, PsiClass[]> convertToTopLevelClasses(final PsiElement[] elements,
                                                                   final boolean fromUpdate,
                                                                   String relativePath,
                                                                   Map<PsiFile, String> relativeMap) {
    final Map<PsiFile, PsiClass[]> result = new HashMap<>();
    for (PsiElement element : elements) {
      final PsiElement navigationElement = element.getNavigationElement();
      LOG.assertTrue(navigationElement != null, element);
      final PsiFile containingFile = navigationElement.getContainingFile();
      if (!(containingFile instanceof PsiClassOwner &&
            JavaProjectRootsUtil.isOutsideJavaSourceRoot(containingFile))) {
        if (containingFile != null) {
          if (PsiPackage.PACKAGE_INFO_CLS_FILE.equals(containingFile.getName()) || 
              containingFile.getContainingDirectory() == null) {
            continue;
          }
        }
        PsiClass[] topLevelClasses = getTopLevelClasses(element);
        if (topLevelClasses == null) {
          if (element instanceof PsiDirectory) {
            if (!fromUpdate) {
              final String name = ((PsiDirectory)element).getName();
              final String path = relativePath != null ? (relativePath.length() > 0 ? (relativePath + "/") : "") + name : null;
              final Map<PsiFile, PsiClass[]> map = convertToTopLevelClasses(element.getChildren(), false, path, relativeMap);
              if (map == null) return null;
              for (Map.Entry<PsiFile, PsiClass[]> entry : map.entrySet()) {
                fillResultsMap(result, entry.getKey(), entry.getValue());
              }
            }
            continue;
          }
          if (!(element instanceof PsiFileSystemItem)) return null;
        }
        fillResultsMap(result, containingFile, topLevelClasses);
        if (relativeMap != null) {
          relativeMap.put(containingFile, relativePath);
        }
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    else {
      boolean hasClasses = false;
      for (PsiClass[] classes : result.values()) {
        if (classes != null) {
          hasClasses = true;
          break;
        }
      }
      return hasClasses ? result : null;
    }
  }

  @Nullable
  private static String normalizeRelativeMap(Map<PsiFile, String> relativeMap) {
    String vector = null;
    for (String relativePath : relativeMap.values()) {
      if (vector == null) {
        vector = relativePath;
      }
      else if (vector.startsWith(relativePath + "/")) {
        vector = relativePath;
      }
      else if (!relativePath.startsWith(vector + "/") && !relativePath.equals(vector)) {
        return null;
      }
    }
    if (vector != null) {
      for (PsiFile psiFile : relativeMap.keySet()) {
        final String path = relativeMap.get(psiFile);
        relativeMap.put(psiFile, path.equals(vector) ? "" : path.substring(vector.length() + 1));
      }
    }
    return vector;
  }

  private static void fillResultsMap(Map<PsiFile, PsiClass[]> result, PsiFile containingFile, PsiClass[] topLevelClasses) {
    PsiClass[] classes = result.get(containingFile);
    if (topLevelClasses != null) {
      if (classes != null) {
        topLevelClasses = ArrayUtil.mergeArrays(classes, topLevelClasses, PsiClass.ARRAY_FACTORY);
      }
      result.put(containingFile, topLevelClasses);
    }
    else {
      result.put(containingFile, classes);
    }
  }

  @Override
  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    final HashMap<PsiFile, String> relativePathsMap = new HashMap<>();
    final Map<PsiFile, PsiClass[]> classes = convertToTopLevelClasses(elements, false, "", relativePathsMap);
    assert classes != null;
    if (defaultTargetDirectory == null) {
      final PsiFile psiFile = classes.keySet().iterator().next();
      defaultTargetDirectory = psiFile.getContainingDirectory();
      LOG.assertTrue(defaultTargetDirectory != null, psiFile);
    }
    Project project = defaultTargetDirectory.getProject();
    if (DumbService.isDumb(project)) {
      int copyDumb = Messages.showYesNoDialog(project,
                                              JavaRefactoringBundle.message("copy.handler.is.dumb.during.indexing"),
                                              getActionName(elements), Messages.getQuestionIcon());
      if (copyDumb == Messages.YES) {
        copyAsFiles(elements, defaultTargetDirectory, project);
      }
      return;
    }

    VirtualFile sourceRootForFile =
      ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(defaultTargetDirectory.getVirtualFile());
    if (sourceRootForFile == null) {
      copyAsFiles(elements, defaultTargetDirectory, project);
      return;
    }
    Object targetDirectory = null;
    String className = null;
    boolean openInEditor = true;
    if (copyOneClass(classes)) {
      final String commonPath =
        ArrayUtilRt.find(elements, classes.values().iterator().next()) == -1 ? normalizeRelativeMap(relativePathsMap) : null;
      CopyClassDialog dialog = new CopyClassDialog(classes.values().iterator().next()[0], defaultTargetDirectory, project, false) {
        @Override
        protected String getQualifiedName() {
          final String qualifiedName = super.getQualifiedName();
          if (commonPath != null && !commonPath.isEmpty() && !qualifiedName.endsWith(commonPath)) {
            return StringUtil.getQualifiedName(qualifiedName, commonPath.replaceAll("/", "."));
          }
          return qualifiedName;
        }
      };
      dialog.setTitle(JavaRefactoringBundle.message("copy.handler.copy.class"));
      if (dialog.showAndGet()) {
        openInEditor = dialog.isOpenInEditor();
        targetDirectory = dialog.getTargetDirectory();
        className = dialog.getClassName();
        if (className == null || className.length() == 0) return;
      }
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        targetDirectory = defaultTargetDirectory;
      }
      else {
        defaultTargetDirectory = CopyFilesOrDirectoriesHandler.resolveDirectory(defaultTargetDirectory);
        if (defaultTargetDirectory == null) return;
        PsiFile[] files = PsiUtilCore.toPsiFileArray(classes.keySet());
        final CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(files, defaultTargetDirectory, project, false) {
          @Override
          public JComponent getPreferredFocusedComponent() {
            return files.length == 1 ? getTargetDirectoryComponent() : super.getPreferredFocusedComponent();
          }
        };
        if (dialog.showAndGet()) {
          targetDirectory = dialog.getTargetDirectory();
          String newName = dialog.getNewName();
          if (files.length == 1) { //strip file extension when multiple classes exist in one file
            className = StringUtil.trimEnd(newName, "." + getFileExtension(files[0]));
          }
          openInEditor = dialog.isOpenInEditor();
        }
      }
    }
    if (targetDirectory != null) {
      copyClassesImpl(className, project, classes, relativePathsMap, targetDirectory, defaultTargetDirectory, JavaRefactoringBundle.message(
        "copy.handler.copy.class"), false, openInEditor);
    }
  }

  private static void copyAsFiles(PsiElement[] elements, PsiDirectory defaultTargetDirectory, Project project) {
    final List<PsiElement> files = new ArrayList<>();
    for (PsiElement element : elements) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) {
        files.add(containingFile);
      }
      else if (element instanceof PsiDirectory) {
        files.add(element);
      }
    }
    CopyFilesOrDirectoriesHandler.copyAsFiles(files.toArray(PsiElement.EMPTY_ARRAY), defaultTargetDirectory, project);
  }

  private static boolean copyOneClass(Map<PsiFile, PsiClass[]> classes) {
    if (classes.size() == 1) {
      final PsiClass[] psiClasses = classes.values().iterator().next();
      return psiClasses != null && psiClasses.length == 1;
    }
    return false;
  }

  @Override
  public void doClone(PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass[] classes = getTopLevelClasses(element);
    if (classes == null) {
      CopyFilesOrDirectoriesHandler.doCloneFile(element);
      return;
    }
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(classes[0], null, project, true);
    dialog.setTitle(JavaRefactoringBundle.message("copy.handler.clone.class"));
    if (dialog.showAndGet()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassesImpl(className, project, Collections.singletonMap(classes[0].getContainingFile(), classes), null, targetDirectory,
                      targetDirectory, JavaRefactoringBundle.message("copy.handler.clone.class"), true, true);
    }
  }

  private static void copyClassesImpl(final String copyClassName,
                                      final Project project,
                                      final Map<PsiFile, PsiClass[]> classes,
                                      final HashMap<PsiFile, String> map,
                                      final Object targetDirectory,
                                      final PsiDirectory defaultTargetDirectory,
                                      final @NlsContexts.Command String commandName,
                                      final boolean selectInActivePanel,
                                      final boolean openInEditor) {
    Runnable command = () -> {
      PsiDirectory target;
      if (targetDirectory instanceof PsiDirectory) {
        target = (PsiDirectory)targetDirectory;
      }
      else {
        target = WriteAction.compute(() -> ((MoveDestination)targetDirectory).getTargetDirectory(defaultTargetDirectory));
      }
      try {
        Collection<PsiFile> files = doCopyClasses(classes, map, copyClassName, target, project);
        if (openInEditor) {
          for (PsiFile file : files) {
            CopyHandler.updateSelectionInActiveProjectView(file, project, selectInActivePanel);
          }
          EditorHelper.openFilesInEditor(files.toArray(PsiFile.EMPTY_ARRAY));
        }
      }
      catch (IncorrectOperationException ex) {
        Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);
  }

  @NotNull
  public static Collection<PsiFile> doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                                  final String copyClassName,
                                                  final PsiDirectory targetDirectory,
                                                  final Project project) throws IncorrectOperationException {
    return doCopyClasses(fileToClasses, null, copyClassName, targetDirectory, project);
  }

  private static List<PsiFile> checkExistingFiles(@NotNull Collection<PsiFile> files, @NotNull PsiDirectory directory,
                                                  @Nullable Map<PsiFile, String> relativePaths, @Nullable String className){
    SkipOverwriteChoice choice = SkipOverwriteChoice.OVERWRITE;
    List<PsiFile> filesToProcess = new ArrayList<>();
    for (PsiFile file : files) {
      final String relativePath = relativePaths != null ? relativePaths.get(file) : null;
      final PsiDirectory targetDirectory = buildRelativeDir(directory, relativePath).getTargetDirectory();
      final String fileName = getNewFileName(file, className);
      final PsiFile existingFile = targetDirectory != null ? targetDirectory.findFile(fileName) : null;
      if (existingFile != null) {
        if (choice != SkipOverwriteChoice.SKIP_ALL && choice != SkipOverwriteChoice.OVERWRITE_ALL) {
          String message = ExecutionBundle.message("copy.classes.command.name");
          choice = SkipOverwriteChoice.askUser(targetDirectory, fileName, message, files.size() > 1);
        }
      }
      if (existingFile == null || choice == SkipOverwriteChoice.OVERWRITE || choice == SkipOverwriteChoice.OVERWRITE_ALL) {
        if (existingFile != file) filesToProcess.add(file);
      }
    }
    return filesToProcess;
  }

  private static <E extends IncorrectOperationException> void runWriteAction(@NotNull Project project,
                                                                             @NotNull @NlsContexts.ProgressTitle String title,
                                                                             @NotNull ThrowableConsumer<@Nullable ProgressIndicator, E> consumer) throws E {
    if (Registry.is("run.refactorings.under.progress")){
      ApplicationEx application = ApplicationManagerEx.getApplicationEx();
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      application.runWriteActionWithCancellableProgressInDispatchThread(title, project, null, progress -> {
        try {
          consumer.consume(progress);
        }
        catch (Throwable e) {
          thrown.set(e);
        }
      });
      Throwable throwable = thrown.get();
      if (throwable instanceof IncorrectOperationException) {
        throw (IncorrectOperationException) throwable;
      } else if (throwable != null) {
        throw new IncorrectOperationException(throwable);
      }
    }
    else {
      WriteAction.run(() -> consumer.consume(null));
    }
  }

  @NotNull
  public static Collection<PsiFile> doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                                  @Nullable HashMap<PsiFile, String> fileToRelativePath, final String copyClassName,
                                                  final PsiDirectory targetDirectory,
                                                  final Project project) throws IncorrectOperationException {
    final Map<PsiClass, PsiClass> oldToNewMap = new HashMap<>();
    final List<PsiFile> createdFiles = new ArrayList<>(fileToClasses.size());
    final List<PsiFile> filesToProcess = checkExistingFiles(fileToClasses.keySet(), targetDirectory, fileToRelativePath, copyClassName);

    runWriteAction(project, RefactoringBundle.message("command.name.copy"), progress -> {
      for (int i = 0; i < filesToProcess.size(); i++) {
        final PsiFile psiFile = filesToProcess.get(i);
        if (progress != null) {
          progress.setIndeterminate(false);
          progress.setFraction((float)i/filesToProcess.size());
          progress.setText2(InspectionsBundle.message("processing.progress.text", psiFile.getName()));
        }
        final PsiClass[] sources = fileToClasses.get(psiFile);
        final String relativePath = fileToRelativePath != null ? fileToRelativePath.get(psiFile) : null;
        final PsiDirectoryImpl directory = WriteAction.compute(() ->
          (PsiDirectoryImpl) buildRelativeDir(targetDirectory, relativePath).findOrCreateTargetDirectory()
        );
        Ref<PsiFile> createdFileReference = new Ref<>();
        directory.executeWithUpdatingAddedFilesDisabled(() -> createdFileReference.set(copy(directory, psiFile, copyClassName)));
        final PsiFile createdFile = createdFileReference.get();
        if (createdFile == null) continue;
        createdFiles.add(createdFile);

        if (createdFile instanceof PsiClassOwner && sources != null) {
          Map<PsiClass, PsiClass> sourceToDestination = new LinkedHashMap<>();
          for (final PsiClass destination : ((PsiClassOwner)createdFile).getClasses()) {
            if (!isSynthetic(destination)) {
              PsiClass source = findByName(sources, destination.getName());
              if (source == null) {
                WriteAction.run(() -> destination.delete());
              }
              else {
                sourceToDestination.put(source, destination);
              }
            }
          }

          for (final Map.Entry<PsiClass, PsiClass> classEntry : sourceToDestination.entrySet()) {
            if (copyClassName != null && sourceToDestination.size() == 1) {
              final PsiClass copy = copy(classEntry.getKey(), copyClassName);
              PsiClass newClass = WriteAction.compute(() -> (PsiClass) classEntry.getValue().replace(copy));
              oldToNewMap.put(classEntry.getKey(), newClass);
            }
            else {
              oldToNewMap.put(classEntry.getKey(), classEntry.getValue());
            }
          }
        }
      }
    });

    DumbService.getInstance(project).completeJustSubmittedTasks();
    CopyFilesOrDirectoriesHandler.updateAddedFiles(createdFiles);

    runWriteAction(project, RefactoringBundle.message("copy.update.references"), progress -> {
      final Set<PsiElement> rebindExpressions = new HashSet<>();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      float numberOfProcessedClasses = 0;
      for (PsiClass copiedClass : oldToNewMap.values()) {
        if (copiedClass == null) {
          LOG.error(oldToNewMap.keySet());
          continue;
        }
        if (progress != null) {
          progress.setIndeterminate(false);
          progress.setFraction(numberOfProcessedClasses/filesToProcess.size());
          progress.setText2(InspectionsBundle.message("processing.progress.text", copiedClass.getName()));
          numberOfProcessedClasses++;
        }
        decodeRefs(copiedClass, oldToNewMap, rebindExpressions);
        final PsiFile psiFile = copiedClass.getContainingFile();
        if (psiFile instanceof PsiJavaFile) {
          codeStyleManager.removeRedundantImports((PsiJavaFile)psiFile);
        }
        for (PsiElement expression : rebindExpressions) {
          //filter out invalid elements which are produced by nested elements:
          //new expressions/type elements, like: List<List<String>>; new Foo(new Foo()), etc
          if (expression.isValid()) {
            codeStyleManager.shortenClassReferences(expression);
          }
        }
      }
    });

    new OptimizeImportsProcessor(project, createdFiles.toArray(PsiFile.EMPTY_ARRAY), null).run();
    return createdFiles;
  }

  protected static boolean isSynthetic(PsiClass aClass) {
    return aClass instanceof SyntheticElement || !aClass.isPhysical();
  }

  private static PsiFile copy(@NotNull PsiDirectory directory, @NotNull PsiFile file, String name) {
    final String fileName = getNewFileName(file, name);
    final PsiFile existingFile = directory.findFile(fileName);
    return WriteAction.compute(() -> {
      if (existingFile != null) existingFile.delete();
      return directory.copyFileFrom(fileName, file);
    });
  }

  private static String getNewFileName(PsiFile file, String name) {
    if (name != null) {
      String fileExtension = getFileExtension(file);
      return StringUtil.isEmpty(fileExtension) ? name : StringUtil.getQualifiedName(name, fileExtension);
    }
    return file.getName();
  }

  private static String getFileExtension(PsiFile file) {
    if (file instanceof PsiClassOwner) {
      for (final PsiClass psiClass : ((PsiClassOwner)file).getClasses()) {
        if (!isSynthetic(psiClass)) {
          return file.getViewProvider().getVirtualFile().getExtension();
        }
      }
    }
    return "";
  }

  @NotNull
  private static MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper buildRelativeDir(final @NotNull PsiDirectory directory,
                                                                                           final @Nullable String relativePath) {
    if (StringUtil.isEmpty(relativePath)) return new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(directory);
    MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper current = null;
    for (String pathElement : relativePath.split("/")) {
      if (current == null) {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(directory, pathElement);
      }
      else {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(current, pathElement);
      }
    }
    LOG.assertTrue(current != null);
    return current;
  }

  private static PsiClass copy(@NotNull PsiClass aClass, @NotNull String name) {
    final PsiClass classNavigationElement = (PsiClass)aClass.getNavigationElement();
    final PsiClass classCopy = (PsiClass)classNavigationElement.copy();
    classCopy.setName(name);
    return classCopy;
  }

  @Nullable
  private static PsiClass findByName(PsiClass[] classes, String name) {
    if (name != null) {
      for (PsiClass aClass : classes) {
        if (name.equals(aClass.getName())) {
          return aClass;
        }
      }
    }
    return null;
  }

  private static void rebindExternalReferences(PsiElement element,
                                               Map<PsiClass, PsiClass> oldToNewMap,
                                               Set<? super PsiElement> rebindExpressions) {
    final LocalSearchScope searchScope = new LocalSearchScope(element);
    for (PsiClass aClass : oldToNewMap.keySet()) {
      final PsiElement newClass = oldToNewMap.get(aClass);
      for (PsiReference reference : ReferencesSearch.search(aClass, searchScope)) {
        rebindExpressions.add(reference.bindToElement(newClass));
      }
    }
  }


  private static void decodeRefs(@NotNull PsiElement element,
                                 final Map<PsiClass, PsiClass> oldToNewMap,
                                 final Set<? super PsiElement> rebindExpressions) {
    final Map<PsiJavaCodeReferenceElement, PsiElement> rebindMap = new LinkedHashMap<>();
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        decodeRef(reference, oldToNewMap, rebindMap);
      }
    });
    for (Map.Entry<PsiJavaCodeReferenceElement, PsiElement> entry : rebindMap.entrySet()) {
      rebindExpressions.add(entry.getKey().bindToElement(entry.getValue()));
    }
    rebindExternalReferences(element, oldToNewMap, rebindExpressions);
  }

  private static void decodeRef(final PsiJavaCodeReferenceElement expression,
                                final Map<PsiClass, PsiClass> oldToNewMap,
                                Map<PsiJavaCodeReferenceElement, PsiElement> rebindExpressions) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)resolved;
      if (oldToNewMap.containsKey(psiClass)) {
        rebindExpressions.put(expression, oldToNewMap.get(psiClass));
      }
    }
  }

  private static PsiClass @Nullable [] getTopLevelClasses(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass &&
          element.getParent() != null &&
          ((PsiClass)element).getContainingClass() == null &&
          !(element instanceof PsiAnonymousClass)) {
        break;
      }
      element = element.getParent();
    }
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      ArrayList<PsiClass> buffer = new ArrayList<>();
      for (final PsiClass aClass : classes) {
        if (isSynthetic(aClass)) {
          return null;
        }
        buffer.add(aClass);
      }
      return buffer.toArray(PsiClass.EMPTY_ARRAY);
    }
    return element instanceof PsiClass ? new PsiClass[]{(PsiClass)element} : null;
  }
}

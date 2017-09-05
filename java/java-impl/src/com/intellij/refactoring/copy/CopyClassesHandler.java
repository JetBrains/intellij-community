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
package com.intellij.refactoring.copy;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

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
        if (PsiPackage.PACKAGE_INFO_CLS_FILE.equals(containingFile.getName())) continue;
        PsiClass[] topLevelClasses = getTopLevelClasses(element);
        if (topLevelClasses == null) {
          if (element instanceof PsiDirectory) {
            if (!fromUpdate) {
              final String name = ((PsiDirectory)element).getName();
              final String path = relativePath != null ? (relativePath.length() > 0 ? (relativePath + "/") : "") + name : null;
              final Map<PsiFile, PsiClass[]> map = convertToTopLevelClasses(element.getChildren(), fromUpdate, path, relativeMap);
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
      } else if (vector.startsWith(relativePath + "/")) {
        vector = relativePath;
      } else if (!relativePath.startsWith(vector + "/") && !relativePath.equals(vector)) {
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
    } else {
      result.put(containingFile, classes);
    }
  }

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
    VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(defaultTargetDirectory.getVirtualFile());
    if (sourceRootForFile == null) {
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
      CopyFilesOrDirectoriesHandler.copyAsFiles(files.toArray(new PsiElement[files.size()]), defaultTargetDirectory, project);
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
      dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
      if (dialog.showAndGet()) {
        openInEditor = dialog.openInEditor();
        targetDirectory = dialog.getTargetDirectory();
        className = dialog.getClassName();
        if (className == null || className.length() == 0) return;
      }
    } else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        targetDirectory = defaultTargetDirectory;
      } else {
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
          openInEditor = dialog.openInEditor();
        }
      }
    }
    if (targetDirectory != null) {
      copyClassesImpl(className, project, classes, relativePathsMap, targetDirectory, defaultTargetDirectory, RefactoringBundle.message(
        "copy.handler.copy.class"), false, openInEditor);
    }
  }

  private static boolean copyOneClass(Map<PsiFile, PsiClass[]> classes) {
    if (classes.size() == 1){
      final PsiClass[] psiClasses = classes.values().iterator().next();
      return psiClasses != null && psiClasses.length == 1;
    }
    return false;
  }

  public void doClone(PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass[] classes = getTopLevelClasses(element);
    if (classes == null) {
      CopyFilesOrDirectoriesHandler.doCloneFile(element);
      return;
    }
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(classes[0], null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    if (dialog.showAndGet()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassesImpl(className, project, Collections.singletonMap(classes[0].getContainingFile(), classes), null, targetDirectory,
                      targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true, true);
    }
  }

  private static void copyClassesImpl(final String copyClassName,
                                      final Project project,
                                      final Map<PsiFile, PsiClass[]> classes,
                                      final HashMap<PsiFile, String> map,
                                      final Object targetDirectory,
                                      final PsiDirectory defaultTargetDirectory,
                                      final String commandName,
                                      final boolean selectInActivePanel, 
                                      final boolean openInEditor) {
    final boolean[] result = new boolean[] {false};
    Runnable command = () -> {
      PsiDirectory target;
      if (targetDirectory instanceof PsiDirectory) {
        target = (PsiDirectory)targetDirectory;
      } else {
        target = WriteAction.compute(() -> ((MoveDestination)targetDirectory).getTargetDirectory(defaultTargetDirectory));
      }
      try {
        Collection<PsiFile> files = doCopyClasses(classes, map, copyClassName, target, project);
        if (files != null) {
          if (openInEditor) {
            for (PsiFile file : files) {
              CopyHandler.updateSelectionInActiveProjectView(file, project, selectInActivePanel);
            }
            EditorHelper.openFilesInEditor(files.toArray(new PsiFile[files.size()]));
          }
        }
      }
      catch (IncorrectOperationException ex) {
        Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(() -> ToolWindowManager.getInstance(project).activateEditorComponent());
    }
  }

   @Nullable
  public static Collection<PsiFile> doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                         final String copyClassName,
                                         final PsiDirectory targetDirectory,
                                         final Project project) throws IncorrectOperationException {
     return doCopyClasses(fileToClasses, null, copyClassName, targetDirectory, project);
   }

  @Nullable
  public static Collection<PsiFile> doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                                     @Nullable HashMap<PsiFile, String> map, final String copyClassName,
                                                     final PsiDirectory targetDirectory,
                                                     final Project project) throws IncorrectOperationException {
    PsiElement newElement = null;
    final Map<PsiClass, PsiElement> oldToNewMap = new HashMap<>();
    for (final PsiClass[] psiClasses : fileToClasses.values()) {
      if (psiClasses != null) {
        for (PsiClass aClass : psiClasses) {
          if (isSynthetic(aClass)) {
            continue;
          }
          oldToNewMap.put(aClass, null);
        }
      }
    }
    final List<PsiFile> createdFiles = new ArrayList<>(fileToClasses.size());
    int[] choice = fileToClasses.size() > 1 ? new int[]{-1} : null;
    List<PsiFile> files = new ArrayList<>();
    for (final Map.Entry<PsiFile, PsiClass[]> entry : fileToClasses.entrySet()) {
      final PsiFile psiFile = entry.getKey();
      final PsiClass[] sources = entry.getValue();
      if (psiFile instanceof PsiClassOwner && sources != null) {
        final PsiFile createdFile = copy(psiFile, targetDirectory, copyClassName, map == null ? null : map.get(psiFile), choice);
        if (createdFile == null) {
          //do not touch unmodified classes
          for (PsiClass aClass : ((PsiClassOwner)psiFile).getClasses()) {
            oldToNewMap.remove(aClass);
          }
          continue;
        }
        PsiClass[] classes = ((PsiClassOwner)createdFile).getClasses();
        for (final PsiClass destination : classes) {
          if (isSynthetic(destination)) {
            continue;
          }
          PsiClass source = findByName(sources, destination.getName());
          if (source != null) {
            final PsiClass copy = copy(source, classes.length > 1 ? null : copyClassName);
            newElement = WriteAction.compute(() -> destination.replace(copy));
            oldToNewMap.put(source, newElement);
          }
          else {
            WriteAction.run(() -> destination.delete());
          }
        }
        createdFiles.add(createdFile);
      } else {
        files.add(psiFile);
      }
    }

    
    for (PsiFile file : files) {
      try {
        PsiDirectory finalTarget = targetDirectory;
        final String relativePath = map != null ? map.get(file) : null;
        if (relativePath != null && !relativePath.isEmpty()) {
          finalTarget = WriteAction.compute(() -> buildRelativeDir(targetDirectory, relativePath).findOrCreateTargetDirectory());
        }
        final PsiFile fileCopy = CopyFilesOrDirectoriesHandler.copyToDirectory(file, getNewFileName(file, copyClassName), finalTarget, choice, null);
        if (fileCopy != null) {
          createdFiles.add(fileCopy);
        }
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.getMessage());
      }
    }

    WriteAction.run(() -> {
      final Set<PsiElement> rebindExpressions = new HashSet<>();
      for (PsiElement element : oldToNewMap.values()) {
        if (element == null) {
          LOG.error(oldToNewMap.keySet());
          continue;
        }
        decodeRefs(element, oldToNewMap, rebindExpressions);
      }

      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      for (PsiFile psiFile : createdFiles) {
        if (psiFile instanceof PsiJavaFile) {
          codeStyleManager.removeRedundantImports((PsiJavaFile)psiFile);
        }
      }
      for (PsiElement expression : rebindExpressions) {
        //filter out invalid elements which are produced by nested elements:
        //new expressions/type elements, like: List<List<String>>; new Foo(new Foo()), etc
        if (expression.isValid()) {
          codeStyleManager.shortenClassReferences(expression);
        }
      }
    });

    new OptimizeImportsProcessor(project, createdFiles.toArray(new PsiFile[createdFiles.size()]), null).run();
    return createdFiles;
  }

  protected static boolean isSynthetic(PsiClass aClass) {
    return aClass instanceof SyntheticElement || !aClass.isPhysical();
  }

  private static PsiFile copy(@NotNull PsiFile file, PsiDirectory directory, String name, String relativePath, int[] choice) {
    final String fileName = getNewFileName(file, name);
    if (relativePath != null && !relativePath.isEmpty()) {
      return WriteAction.compute(() -> buildRelativeDir(directory, relativePath).findOrCreateTargetDirectory().copyFileFrom(fileName, file));
    }
    if (CopyFilesOrDirectoriesHandler.checkFileExist(directory, choice, file, fileName, "Copy")) return null;
    return WriteAction.compute(() -> directory.copyFileFrom(fileName, file));
  }

  private static String getNewFileName(PsiFile file, String name) {
    if (name != null) {
      String fileExtension = getFileExtension(file);
      return fileExtension.isEmpty() ? name : StringUtil.getQualifiedName(name, fileExtension);
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
                                                                                           final @NotNull String relativePath) {
    MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper current = null;
    for (String pathElement : relativePath.split("/")) {
      if (current == null) {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(directory, pathElement);
      } else {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(current, pathElement);
      }
    }
    LOG.assertTrue(current != null);
    return current;
  }

  private static PsiClass copy(PsiClass aClass, String name) {
    final PsiClass classNavigationElement = (PsiClass)aClass.getNavigationElement();
    final PsiClass classCopy = (PsiClass)classNavigationElement.copy();
    if (name != null) {
      classCopy.setName(name);
    }
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
                                               Map<PsiClass, PsiElement> oldToNewMap,
                                               Set<PsiElement> rebindExpressions) {
     final LocalSearchScope searchScope = new LocalSearchScope(element);
     for (PsiClass aClass : oldToNewMap.keySet()) {
       final PsiElement newClass = oldToNewMap.get(aClass);
       for (PsiReference reference : ReferencesSearch.search(aClass, searchScope)) {
         rebindExpressions.add(reference.bindToElement(newClass));
       }
     }
   }


  private static void decodeRefs(@NotNull PsiElement element, final Map<PsiClass, PsiElement> oldToNewMap, final Set<PsiElement> rebindExpressions) {
    final Map<PsiJavaCodeReferenceElement, PsiElement> rebindMap = new LinkedHashMap<>();
    element.accept(new JavaRecursiveElementVisitor(){
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
                                final Map<PsiClass, PsiElement> oldToNewMap,
                                Map<PsiJavaCodeReferenceElement, PsiElement> rebindExpressions) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)resolved;
      if (oldToNewMap.containsKey(psiClass)) {
        rebindExpressions.put(expression, oldToNewMap.get(psiClass));
      }
    }
  }

  @Nullable
  private static PsiClass[] getTopLevelClasses(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() != null && ((PsiClass)element).getContainingClass() == null && !(element instanceof PsiAnonymousClass)) break;
      element = element.getParent();
    }
    //if (element instanceof PsiCompiledElement) return null;
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      ArrayList<PsiClass> buffer = new ArrayList<>();
      for (final PsiClass aClass : classes) {
        if (isSynthetic(aClass)) {
          return null;
        }
        buffer.add(aClass);
      }
      return buffer.toArray(new PsiClass[buffer.size()]);
    }
    return element instanceof PsiClass ? new PsiClass[]{(PsiClass)element} : null;
  }
}

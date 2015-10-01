/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class FormatChangedTextUtil {
  public static final Key<CharSequence> TEST_REVISION_CONTENT = Key.create("test.revision.content");
  protected static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.FormatChangedTextUtil");

  protected FormatChangedTextUtil() {
  }

  @NotNull
  public static FormatChangedTextUtil getInstance() {
    return ServiceManager.getService(FormatChangedTextUtil.class);
  }

  /**
   * Allows to answer if given file has changes in comparison with VCS.
   * 
   * @param file  target file
   * @return      <code>true</code> if given file has changes; <code>false</code> otherwise
   */
  public static boolean hasChanges(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final Change change = ChangeListManager.getInstance(project).getChange(virtualFile);
      return change != null;
    }
    return false;
  }

  /**
   * Allows to answer if any file below the given directory (any level of nesting) has changes in comparison with VCS.
   * 
   * @param directory  target directory to check
   * @return           <code>true</code> if any file below the given directory has changes in comparison with VCS;
   *                   <code>false</code> otherwise
   */
  public static boolean hasChanges(@NotNull PsiDirectory directory) {
    return hasChanges(directory.getVirtualFile(), directory.getProject());
  }

  /**
   * Allows to answer if given file or any file below the given directory (any level of nesting) has changes in comparison with VCS.
   * 
   * @param file     target directory to check
   * @param project  target project
   * @return         <code>true</code> if given file or any file below the given directory has changes in comparison with VCS;
   *                 <code>false</code> otherwise
   */
  public static boolean hasChanges(@NotNull VirtualFile file, @NotNull Project project) {
    final Collection<Change> changes = ChangeListManager.getInstance(project).getChangesIn(file);
    for (Change change : changes) {
      if (change.getType() == Change.Type.NEW || change.getType() == Change.Type.MODIFICATION) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasChanges(@NotNull VirtualFile[] files, @NotNull Project project) {
    for (VirtualFile file : files) {
      if (hasChanges(file, project))
        return true;
    }
    return false;
  }

  /**
   * Allows to answer if any file that belongs to the given module has changes in comparison with VCS.
   * 
   * @param module  target module to check
   * @return        <code>true</code> if any file that belongs to the given module has changes in comparison with VCS
   *                <code>false</code> otherwise
   */
  public static boolean hasChanges(@NotNull Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    for (VirtualFile root : rootManager.getSourceRoots()) {
      if (hasChanges(root, module.getProject())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Allows to answer if any file that belongs to the given project has changes in comparison with VCS.
   * 
   * @param project  target project to check
   * @return         <code>true</code> if any file that belongs to the given project has changes in comparison with VCS
   *                 <code>false</code> otherwise
   */
  public static boolean hasChanges(@NotNull final Project project) {
    final ModifiableModuleModel moduleModel = new ReadAction<ModifiableModuleModel>() {
      @Override
      protected void run(@NotNull Result<ModifiableModuleModel> result) throws Throwable {
        result.setResult(ModuleManager.getInstance(project).getModifiableModel());
      }
    }.execute().getResultObject();
    try {
      for (Module module : moduleModel.getModules()) {
        if (hasChanges(module)) {
          return true;
        }
      }
      return false;
    }
    finally {
      moduleModel.dispose();
    }
  }

  @NotNull
  public static List<PsiFile> getChangedFilesFromDirs(@NotNull Project project, @NotNull List<PsiDirectory> dirs)  {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    Collection<Change> changes = ContainerUtil.newArrayList();

    for (PsiDirectory dir : dirs) {
      changes.addAll(changeListManager.getChangesIn(dir.getVirtualFile()));
    }

    return getChangedFiles(project, changes);
  }

  @NotNull
  public static List<PsiFile> getChangedFiles(@NotNull final Project project, @NotNull Collection<Change> changes) {
    Function<Change, PsiFile> changeToPsiFileMapper = new Function<Change, PsiFile>() {
      private PsiManager myPsiManager = PsiManager.getInstance(project);

      @Override
      public PsiFile fun(Change change) {
        VirtualFile vFile = change.getVirtualFile();
        return vFile != null ? myPsiManager.findFile(vFile) : null;
      }
    };

    return ContainerUtil.mapNotNull(changes, changeToPsiFileMapper);
  }

  @NotNull
  public List<TextRange> getChangedTextRanges(@NotNull Project project, @NotNull PsiFile file) throws FilesTooBigForDiffException {
    return ContainerUtil.emptyList();
  }

  public int calculateChangedLinesNumber(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    return -1;
  }

  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    return false;
  }
}

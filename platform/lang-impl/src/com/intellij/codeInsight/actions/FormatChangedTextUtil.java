/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FormatChangedTextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.FormatChangedTextUtil");
  
  private FormatChangedTextUtil() {
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
      if (change != null && change.getType() == Change.Type.NEW) {
        return true;
      }
    }

    final LineStatusTrackerManagerI manager = LineStatusTrackerManager.getInstance(project);
    if (manager == null) {
      return false;
    }
    
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) {
      return false;
    }
    final LineStatusTracker lineStatusTracker = manager.getLineStatusTracker(document);
    if (lineStatusTracker == null) {
      return false;
    }
    final List<Range> ranges = lineStatusTracker.getRanges();
    if (ranges == null || ranges.isEmpty()) {
      return false;
    } 
    for (Range range : ranges) {
      if (range.getType() != Range.DELETED) {
        return true;
      }
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
      protected void run(Result<ModifiableModuleModel> result) throws Throwable {
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

  /**
   * Allows to ask for the changed text of the given file (in comparison with VCS).
   * 
   * @param file  target file
   * @return      collection of changed regions for the given file
   */
  @NotNull
  public static Collection<TextRange> getChanges(@NotNull PsiFile file) {
    final Set<TextRange> defaultResult = Collections.singleton(file.getTextRange());
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final Change change = ChangeListManager.getInstance(file.getProject()).getChange(virtualFile);
      if (change != null && change.getType() == Change.Type.NEW) {
        return defaultResult;
      }
    }

    final LineStatusTrackerManagerI manager = LineStatusTrackerManager.getInstance(file.getProject());
    if (manager == null) {
      return defaultResult;
    }
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return defaultResult;
    }
    final LineStatusTracker lineStatusTracker = manager.getLineStatusTracker(document);
    if (lineStatusTracker == null) {
      return defaultResult;
    }
    final List<Range> ranges = lineStatusTracker.getRanges();
    if (ranges == null || ranges.isEmpty()) {
      return defaultResult;
    }
    
    List<TextRange> result = new ArrayList<TextRange>();
    for (Range range : ranges) {
      if (range.getType() != Range.DELETED) {
        final RangeHighlighter highlighter = range.getHighlighter();
        if (highlighter != null) {
          result.add(new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()));
        }
      }
    }
    return result;
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
  public static List<PsiFile> getChangedFiles(@NotNull Project project, @NotNull Collection<Change> changes) {
    List<PsiFile> files = ContainerUtil.newArrayList();
    for (Change change : changes) {
      VirtualFile vFile = change.getVirtualFile();
      if (vFile != null) {
        PsiFile file = PsiManager.getInstance(project).findFile(vFile);
        if (file != null) files.add(file);
      }
    }
    return files;
  }

  @NotNull
  public static List<TextRange> getChangedTextRanges(@NotNull Project project, @NotNull PsiFile file) {
    Change change = ChangeListManager.getInstance(project).getChange(file.getVirtualFile());
    if (change == null) {
      return ContainerUtilRt.emptyList();
    }
    if (change.getType() == Change.Type.NEW) {
      return ContainerUtil.newArrayList(file.getTextRange());
    }

    String contentFromVcs = getRevisionedContentFrom(change);
    return contentFromVcs != null ? calculateChangedTextRanges(project, file, contentFromVcs)
                                  : ContainerUtil.<TextRange>emptyList();
  }

  @Nullable
  private static String getRevisionedContentFrom(@NotNull Change change) {
    ContentRevision revision = change.getBeforeRevision();
    if (revision == null) {
      return null;
    }

    try {
      return revision.getContent();
    }
    catch (VcsException e) {
      LOG.error("Can't get content for: " + change.getVirtualFile(), e);
      return null;
    }
  }

  @NotNull
  private static List<TextRange> calculateChangedTextRanges(@NotNull Project project, @NotNull PsiFile file, @NotNull String contentFromVcs) {
    Document documentFromVcs = EditorFactory.getInstance().createDocument(contentFromVcs);
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);

    if (document == null) {
      return ContainerUtil.emptyList();
    }

    try {
      List<Range> changedRanges = new RangesBuilder(document, documentFromVcs).getRanges();
      return getChangedTextRanges(document, changedRanges);
    }
    catch (FilesTooBigForDiffException e) {
      LOG.error("Error while calculating changed ranges for: " + file.getVirtualFile(), e);
      return ContainerUtil.emptyList();
    }
  }

  @NotNull
  private static List<TextRange> getChangedTextRanges(@NotNull Document document, @NotNull List<Range> changedRanges) {
    List<TextRange> ranges = ContainerUtil.newArrayList();
    for (Range range : changedRanges) {
      if (range.getType() != Range.DELETED) {
        int changeStartLine = range.getOffset1();
        int changeEndLine = range.getOffset2();

        int lineStartOffset = document.getLineStartOffset(changeStartLine);
        int lineEndOffset = document.getLineEndOffset(changeEndLine - 1);

        ranges.add(new TextRange(lineStartOffset, lineEndOffset));
      }
    }
    return ranges;
  }
}

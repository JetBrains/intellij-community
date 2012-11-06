/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiFilter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class PsiChangeTracker {
  private PsiChangeTracker() {
  }

  public static <T extends PsiElement> Map<T, FileStatus> getElementsChanged(PsiFile file, final PsiFilter<T> filter) {
    final Project project = file.getProject();
    final VirtualFile vf = file.getVirtualFile();

    if (vf == null) return Collections.emptyMap();

    final String oldText = getUnmodifiedDocument(vf, project);
    //TODO: make loop for different languages
    //TODO: for ( PsiFile f : file.getViewProvider().getAllFiles() )
    //TODO: for some languages (eg XML) isEquivalentTo works ugly. Think about pluggable matchers for different languages/elements
    final PsiFile oldFile = oldText == null
                            ? null : PsiFileFactory.getInstance(project).createFileFromText(oldText, file);
    return getElementsChanged(file, oldFile, filter);
  }

  public static <T extends PsiElement> Map<T, FileStatus> getElementsChanged(PsiElement file,
                                                                             PsiElement oldFile,
                                                                             final PsiFilter<T> filter) {
    final HashMap<T, FileStatus> result = new HashMap<T, FileStatus>();
    final List<T> oldElements = new ArrayList<T>();
    final List<T> elements = new ArrayList<T>();

    if (file == null) {
      oldFile.accept(filter.createVisitor(oldElements));
      calculateStatuses(elements, oldElements, result, filter);
      return result;
    }

    final Project project = file.getProject();

    file.accept(filter.createVisitor(elements));
    final VirtualFile vf = file.getContainingFile().getVirtualFile();
    FileStatus status = vf == null ? null : FileStatusManager.getInstance(project).getStatus(vf);
    if (status == null && oldFile == null) {
      status = FileStatus.ADDED;
    }
    if (status == FileStatus.ADDED ||
        status == FileStatus.DELETED ||
        status == FileStatus.DELETED_FROM_FS ||
        status == FileStatus.UNKNOWN) {
      for (T element : elements) {
        result.put(element, status);
      }
      return result;
    }

    if (oldFile == null) return result;
    oldFile.accept(filter.createVisitor(oldElements));
    calculateStatuses(elements, oldElements, result, filter);

    return result;
  }

  private static <T extends PsiElement> Map<T, FileStatus> calculateStatuses(List<T> elements,
                                                                             List<T> oldElements,
                                                                             Map<T, FileStatus> result, PsiFilter<T> filter) {
    for (T element : elements) {
      T e = null;
      for (T oldElement : oldElements) {
        if (filter.areEquivalent(element, oldElement)) {
          e = oldElement;
          break;
        }
      }
      if (e != null) {
        oldElements.remove(e);
        if (!element.getText().equals(e.getText())) {
          result.put(element, FileStatus.MODIFIED);
        }
      }
      else {
        result.put(element, FileStatus.ADDED);
      }
    }

    for (T oldElement : oldElements) {
      result.put(oldElement, FileStatus.DELETED);
    }

    return result;
  }

  @Nullable
  private static String getUnmodifiedDocument(final VirtualFile file, Project project) {
    final Change change = ChangeListManager.getInstance(project).getChange(file);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision instanceof BinaryContentRevision) {
        return null;
      }
      if (beforeRevision != null) {
        String content;
        try {
          content = beforeRevision.getContent();
        }
        catch (VcsException ex) {
          content = null;
        }
        return content == null ? null : StringUtil.convertLineSeparators(content);
      }
      return null;
    }

    if (FileDocumentManager.getInstance().isFileModified(file)) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return LoadTextUtil.loadText(file).toString();
        }
      });
    }

    return null;
  }
}

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
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* User: cdr
*/
public class SelfElementInfo extends SmartPointerElementInfo {
  private static final FileDocumentManager ourFileDocManager = FileDocumentManager.getInstance();
  protected volatile Class myType;
  private final SmartPointerManagerImpl myManager;
  protected final Language myLanguage;
  protected final MarkerCache myMarkerCache;
  private final boolean myForInjected;
  private int myStartOffset;
  private int myEndOffset;

  SelfElementInfo(@NotNull Project project,
                  @Nullable ProperTextRange range,
                  @NotNull Class anchorClass,
                  @NotNull PsiFile containingFile,
                  @NotNull Language language,
                  boolean forInjected) {
    myLanguage = language;
    myForInjected = forInjected;
    myType = anchorClass;
    assert !PsiFile.class.isAssignableFrom(anchorClass) : "FileElementInfo must be used for files";

    myManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(project);
    myMarkerCache = myManager.getMarkerCache(containingFile.getViewProvider().getVirtualFile());
    setRange(range);
    myMarkerCache.rangeChanged();
  }

  void setRange(@Nullable Segment range) {
    if (range != null) {
      myStartOffset = range.getStartOffset();
      myEndOffset = range.getEndOffset();
    } else {
      myStartOffset = -1;
      myEndOffset = -1;
    }
  }

  boolean hasRange() {
    return myStartOffset >= 0;
  }

  int getPsiStartOffset() {
    return myStartOffset;
  }

  int getPsiEndOffset() {
    return myEndOffset;
  }

  boolean isForInjected() {
    return myForInjected;
  }

  @Override
  public Document getDocumentToSynchronize() {
    return ourFileDocManager.getCachedDocument(getVirtualFile());
  }

  @Override
  public PsiElement restoreElement() {
    Segment segment = getPsiRange();
    if (segment == null) return null;

    PsiFile file = restoreFile();
    if (file == null || !file.isValid()) return null;

    return findElementInside(file, segment.getStartOffset(), segment.getEndOffset(), myType, myLanguage);
  }

  @Nullable
  @Override
  public ProperTextRange getPsiRange() {
    return calcPsiRange();
  }

  @Nullable
  private ProperTextRange calcPsiRange() {
    return hasRange() ? ProperTextRange.create(myStartOffset, myEndOffset) : null;
  }

  @Override
  public PsiFile restoreFile() {
    return restoreFileFromVirtual(getVirtualFile(), getProject(), myLanguage);
  }

  public static PsiElement findElementInside(@NotNull PsiFile file,
                                      int syncStartOffset,
                                      int syncEndOffset,
                                      @NotNull Class type,
                                      @NotNull Language language) {
    PsiElement anchor = file.getViewProvider().findElementAt(syncStartOffset, language);
    if (anchor == null && syncStartOffset == file.getTextLength()) {
      PsiElement lastChild = file.getViewProvider().getPsi(language).getLastChild();
      if (lastChild != null) {
        anchor = PsiTreeUtil.getDeepestLast(lastChild);
      }
    }
    if (anchor == null) return null;

    PsiElement result = findParent(syncStartOffset, syncEndOffset, type, anchor);
    if (syncEndOffset == syncStartOffset) {
      while (result == null && anchor.getTextRange().getStartOffset() == syncEndOffset) {
        anchor = PsiTreeUtil.prevLeaf(anchor, false);
        if (anchor == null) break;

        result = findParent(syncStartOffset, syncEndOffset, type, anchor);
      }
    }
    return result;
  }

  @Nullable
  private static PsiElement findParent(int syncStartOffset, int syncEndOffset, @NotNull Class type, PsiElement anchor) {
    TextRange range = anchor.getTextRange();

    if (range.getStartOffset() != syncStartOffset) return null;
    while (range.getEndOffset() < syncEndOffset) {
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) {
        return null;
      }
      range = anchor.getTextRange();
    }

    while (range.getEndOffset() == syncEndOffset) {
      if (type.equals(anchor.getClass())) {
        return anchor;
      }
      anchor = anchor.getParent();
      if (anchor == null || anchor.getTextRange() == null) break;
      range = anchor.getTextRange();
    }

    return null;
  }

  @Override
  public void cleanup() {
    setRange(null);
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(@NotNull final VirtualFile virtualFile, @NotNull final Project project, @Nullable final Language language) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiFile>() {
      @Override
      public PsiFile compute() {
        if (project.isDisposed()) return null;
        VirtualFile child;
        if (virtualFile.isValid()) {
          child = virtualFile;
        }
        else {
          VirtualFile vParent = virtualFile.getParent();
          if (vParent == null || !vParent.isDirectory()) return null;
          String name = virtualFile.getName();
          child = vParent.findChild(name);
        }
        if (child == null || !child.isValid()) return null;
        PsiFile file = PsiManager.getInstance(project).findFile(child);
        if (file != null && language != null) {
          return file.getViewProvider().getPsi(language);
        }

        return file;
      }
    });
  }

  @Nullable
  public static PsiDirectory restoreDirectoryFromVirtual(final VirtualFile virtualFile, @NotNull final Project project) {
    if (virtualFile == null) return null;

    return ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
      @Override
      public PsiDirectory compute() {
        VirtualFile child;
        if (virtualFile.isValid()) {
          child = virtualFile;
        }
        else {
          VirtualFile vParent = virtualFile.getParent();
          if (vParent == null || !vParent.isDirectory()) return null;
          String name = virtualFile.getName();
          child = vParent.findChild(name);
        }
        if (child == null || !child.isValid()) return null;
        PsiDirectory file = PsiManager.getInstance(project).findDirectory(child);
        if (file == null || !file.isValid()) return null;
        return file;
      }
    });
  }

  @Override
  public int elementHashCode() {
    return getVirtualFile().hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull final SmartPointerElementInfo other) {
    if (other instanceof SelfElementInfo) {
      SelfElementInfo otherInfo = (SelfElementInfo)other;
      Segment range1 = getPsiRange();
      Segment range2 = otherInfo.getPsiRange();
      return Comparing.equal(getVirtualFile(), otherInfo.getVirtualFile())
             && myType == otherInfo.myType
             && range1 != null
             && range2 != null
             && range1.getStartOffset() == range2.getStartOffset()
             && range1.getEndOffset() == range2.getEndOffset()
        ;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return Comparing.equal(restoreElement(), other.restoreElement());
      }
    });
  }

  @Override
  @NotNull
  public final VirtualFile getVirtualFile() {
    return myMarkerCache.getVirtualFile();
  }

  @Override
  @Nullable
  public Segment getRange() {
    if (hasRange()) {
      Document document = getDocumentToSynchronize();
      if (document != null) {
        PsiDocumentManagerBase documentManager = myManager.getPsiDocumentManager();
        List<DocumentEvent> events = documentManager.getEventsSinceCommit(document);
        if (!events.isEmpty()) {
          return myMarkerCache.getUpdatedRange(this, (FrozenDocument)documentManager.getLastCommittedDocument(document), events);
        }
      }
    }
    return calcPsiRange();
  }

  @NotNull
  @Override
  public final Project getProject() {
    return myManager.getProject();
  }

}

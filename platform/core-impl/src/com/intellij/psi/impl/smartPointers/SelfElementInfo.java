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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SelfElementInfo extends SmartPointerElementInfo {
  private static final FileDocumentManager ourFileDocManager = FileDocumentManager.getInstance();
  private volatile Identikit myIdentikit;
  protected final SmartPointerManagerImpl myManager;
  private final VirtualFile myFile;
  private final boolean myForInjected;
  private int myStartOffset;
  private int myEndOffset;

  SelfElementInfo(@NotNull Project project,
                  @Nullable ProperTextRange range,
                  @NotNull Identikit identikit,
                  @NotNull PsiFile containingFile,
                  boolean forInjected) {
    myForInjected = forInjected;
    myIdentikit = identikit;

    myManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(project);
    myFile = containingFile.getViewProvider().getVirtualFile();
    setRange(range);
  }

  void switchToAnchor(@NotNull PsiElement element) {
    Pair<Identikit.ByAnchor, PsiElement> pair = Identikit.withAnchor(element, myIdentikit.getFileLanguage());
    if (pair != null) {
      assert pair.first.hashCode() == myIdentikit.hashCode();
      myIdentikit = pair.first;
      setRange(pair.second.getTextRange());
    } else {
      setRange(element.getTextRange());
    }
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

  boolean isGreedy() {
    return myForInjected || myIdentikit.isForPsiFile();
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

    return myIdentikit.findPsiElement(file, segment.getStartOffset(), segment.getEndOffset());
  }

  @Nullable
  @Override
  public TextRange getPsiRange() {
    return calcPsiRange();
  }

  @Nullable
  private TextRange calcPsiRange() {
    return hasRange() ? new UnfairTextRange(myStartOffset, myEndOffset) : null;
  }

  @Override
  public PsiFile restoreFile() {
    return restoreFileFromVirtual(getVirtualFile(), getProject(), myIdentikit.getFileLanguage());
  }

  @Override
  public void cleanup() {
    setRange(null);
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(@NotNull VirtualFile virtualFile, @NotNull Project project, @NotNull Language language) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return null;
      VirtualFile child = restoreVFile(virtualFile);
      if (child == null || !child.isValid()) return null;
      PsiFile file = PsiManager.getInstance(project).findFile(child);
      if (file != null) {
        return file.getViewProvider().getPsi(language == Language.ANY ? file.getViewProvider().getBaseLanguage() : language);
      }

      return null;
    });
  }

  @Nullable
  public static PsiDirectory restoreDirectoryFromVirtual(final VirtualFile virtualFile, @NotNull final Project project) {
    if (virtualFile == null) return null;

    return ReadAction.compute(() -> {
      if (project.isDisposed()) return null;
      VirtualFile child = restoreVFile(virtualFile);
      if (child == null || !child.isValid()) return null;
      PsiDirectory file = PsiManager.getInstance(project).findDirectory(child);
      if (file == null || !file.isValid()) return null;
      return file;
    });
  }

  @Nullable
  private static VirtualFile restoreVFile(VirtualFile virtualFile) {
    VirtualFile child;
    if (virtualFile.isValid()) {
      child = virtualFile;
    }
    else {
      VirtualFile vParent = virtualFile.getParent();
      if (vParent == null || !vParent.isValid()) return null;
      String name = virtualFile.getName();
      child = vParent.findChild(name);
    }
    return child;
  }

  @Override
  public int elementHashCode() {
    return getVirtualFile().hashCode() + myIdentikit.hashCode() * 31;
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull final SmartPointerElementInfo other) {
    if (other instanceof SelfElementInfo) {
      final SelfElementInfo otherInfo = (SelfElementInfo)other;
      if (!getVirtualFile().equals(other.getVirtualFile()) || myIdentikit != otherInfo.myIdentikit) return false;

      return ReadAction.compute(() -> {
        Segment range1 = getPsiRange();
        Segment range2 = otherInfo.getPsiRange();
        return range1 != null && range2 != null
               && range1.getStartOffset() == range2.getStartOffset()
               && range1.getEndOffset() == range2.getEndOffset();
      });
    }
    return false;
  }

  @Override
  @NotNull
  public final VirtualFile getVirtualFile() {
    return myFile;
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
          SmartPointerTracker tracker = myManager.getTracker(getVirtualFile());
          if (tracker != null) {
            return tracker.getUpdatedRange(this, (FrozenDocument)documentManager.getLastCommittedDocument(document), events);
          }
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

  @Override
  public String toString() {
    return "psi:range=" + calcPsiRange() + ",type=" + myIdentikit;
  }
}

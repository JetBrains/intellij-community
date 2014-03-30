/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public abstract class MultiplePsiFilesPerDocumentFileViewProvider extends SingleRootFileViewProvider {
  private final ConcurrentMap<Language, PsiFile> myRoots = new ConcurrentHashMap<Language, PsiFile>(1, ConcurrentHashMap.DEFAULT_LOAD_FACTOR, 1);
  private MultiplePsiFilesPerDocumentFileViewProvider myOriginal = null;

  public MultiplePsiFilesPerDocumentFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean eventSystemEnabled) {
    super(manager, virtualFile, eventSystemEnabled, Language.ANY);
  }

  @Override
  @NotNull
  public abstract Language getBaseLanguage();

  @Override
  @NotNull
  public List<PsiFile> getAllFiles() {
    final List<PsiFile> roots = new ArrayList<PsiFile>();
    for (Language language : getLanguages()) {
      PsiFile psi = getPsi(language);
      if (psi != null) roots.add(psi);
    }
    final PsiFile base = getPsi(getBaseLanguage());
    if (!roots.isEmpty() && roots.get(0) != base) {
      roots.remove(base);
      roots.add(0, base);
    }
    return roots;
  }

  protected void removeFile(final Language language) {
    myRoots.remove(language);
  }

  @Override
  protected PsiFile getPsiInner(@NotNull final Language target) {
    PsiFile file = myRoots.get(target);
    if (file == null) {
      if (isPhysical()) {
        VirtualFile virtualFile = getVirtualFile();
        if (isIgnored()) return null;
        VirtualFile parent = virtualFile.getParent();
        if (parent != null) {
          getManager().findDirectory(parent);
        }
      }
      file = createFile(target);
      if (file == null) return null;
      if (myOriginal != null) {
        final PsiFile originalFile = myOriginal.getPsi(target);
        if (originalFile != null) {
          ((PsiFileImpl)file).setOriginalFile(originalFile);
        }
      }
      file = ConcurrencyUtil.cacheOrGet(myRoots, target, file);
    }
    return file;
  }


  @Override
  public PsiFile getCachedPsi(Language target) {
    return myRoots.get(target);
  }

  @Override
  public FileElement[] getKnownTreeRoots() {
    List<FileElement> files = new ArrayList<FileElement>(myRoots.size());
    for (PsiFile file : myRoots.values()) {
      final FileElement treeElement = ((PsiFileImpl)file).getTreeElement();
      if (treeElement != null) {
        files.add(treeElement);
      }
    }

    return files.toArray(new FileElement[files.size()]);
  }

  @TestOnly
  public void checkAllTreesEqual() {
    Collection<PsiFile> roots = myRoots.values();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
    documentManager.commitAllDocuments();
    for (PsiFile root : roots) {
      Document document = documentManager.getDocument(root);
      PsiDocumentManagerBase.checkConsistency(root, document);
      assert root.getText().equals(document.getText());
    }
  }

  @NotNull
  @Override
  public final MultiplePsiFilesPerDocumentFileViewProvider createCopy(@NotNull final VirtualFile fileCopy) {
    final MultiplePsiFilesPerDocumentFileViewProvider copy = cloneInner(fileCopy);
    copy.myOriginal = myOriginal == null ? this : myOriginal;
    return copy;
  }

  protected abstract MultiplePsiFilesPerDocumentFileViewProvider cloneInner(VirtualFile fileCopy);

  @Override
  @Nullable
  public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
    final PsiFile mainRoot = getPsi(getBaseLanguage());
    PsiElement ret = null;
    for (final Language language : getLanguages()) {
      if (!ReflectionUtil.isAssignable(lang, language.getClass())) continue;
      if (lang.equals(Language.class) && !getLanguages().contains(language)) continue;

      final PsiFile psiRoot = getPsi(language);
      final PsiElement psiElement = findElementAt(psiRoot, offset);
      if (psiElement == null || psiElement instanceof OuterLanguageElement) continue;
      if (ret == null || psiRoot != mainRoot) {
        ret = psiElement;
      }
    }
    return ret;
  }

  @Override
  @Nullable
  public PsiElement findElementAt(int offset) {
    return findElementAt(offset, Language.class);
  }

  @Override
  @Nullable
  public PsiReference findReferenceAt(int offset) {
    TextRange minRange = new TextRange(0, getContents().length());
    PsiReference ret = null;
    for (final Language language : getLanguages()) {
      final PsiElement psiRoot = getPsi(language);
      final PsiReference reference = SharedPsiElementImplUtil.findReferenceAt(psiRoot, offset, language);
      if (reference == null) continue;
      final TextRange textRange = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
      if (minRange.contains(textRange) && !textRange.contains(minRange)) {
        minRange = textRange;
        ret = reference;
      }
    }
    return ret;
  }

  @Override
  public void contentsSynchronized() {
    super.contentsSynchronized();
    Set<Language> languages = getLanguages();
    for (Iterator<Map.Entry<Language, PsiFile>> iterator = myRoots.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<Language, PsiFile> entry = iterator.next();
      if (!languages.contains(entry.getKey())) {
        iterator.remove();
      }
    }
  }
}

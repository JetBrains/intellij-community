/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author db
 */
public abstract class PsiAnchor {
  @Nullable
  public abstract PsiElement retrieve();
  public abstract PsiFile getFile();
  public abstract int getStartOffset();
  public abstract int getEndOffset();

  public static PsiAnchor create(final PsiElement element) {
    if (element instanceof PsiFile) {
      return new HardReference(element);
    }

    PsiFile file = element.getContainingFile();
    if (file == null) {
      return new HardReference(element);
    }

    if (element instanceof StubBasedPsiElement && element.isPhysical() && (element instanceof PsiCompiledElement || ((PsiFileImpl)file).getContentElementType() instanceof IStubFileElementType)) {
      final StubBasedPsiElement elt = (StubBasedPsiElement)element;
      if (elt.getStub() != null || elt.getElementType().shouldCreateStub(element.getNode())) {
        return new StubIndexReference(file, calcStubIndex((StubBasedPsiElement)element));
      }
    }

    TextRange textRange = element.getTextRange();
    if (textRange == null || element instanceof LightElement) {
      return new HardReference(element);
    }

    Language lang = null;
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<Language> languages = viewProvider.getLanguages();
    for (Language l : languages) {
      if (PsiTreeUtil.isAncestor(viewProvider.getPsi(l), element, false)) {
        lang = l;
        break;
      }
    }

    if (lang == null) lang = element.getLanguage();
    return new TreeRangeReference(file, textRange.getStartOffset(), textRange.getEndOffset(), element.getClass(), lang);
  }

  public static int calcStubIndex(StubBasedPsiElement psi) {
    if (psi instanceof PsiFile) {
      return 0;
    }

    final StubElement liveStub = psi.getStub();
    if (liveStub != null) {
      return ((StubBase)liveStub).id;
    }

    PsiFileImpl file = (PsiFileImpl)psi.getContainingFile();
    final StubTree stubTree = file.calcStubTree();
    for (StubElement<?> stb : stubTree.getPlainList()) {
      if (stb.getPsi() == psi) {
        return ((StubBase)stb).id;
      }
    }

    throw new RuntimeException("Can't find stub index for PSI element " + psi);
  }


  private static class TreeRangeReference extends PsiAnchor {
    private final PsiFile myFile;
    private final Language myLanguage;
    private final int myStartOffset;
    private final int myEndOffset;
    private final Class myClass;

    private TreeRangeReference(final PsiFile file, final int startOffset, final int endOffset, final Class aClass, final Language language) {
      myFile = file;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myClass = aClass;
      myLanguage = language;
    }

    @Nullable
    public PsiElement retrieve() {
      PsiElement element = myFile.getViewProvider().findElementAt(myStartOffset, myLanguage);
      if (element == null) return null;

      while  (!element.getClass().equals(myClass) ||
              element.getTextRange().getStartOffset() != myStartOffset ||
              element.getTextRange().getEndOffset() != myEndOffset) {
        element = element.getParent();
        if (element == null || element.getTextRange() == null) return null;
      }

      return element;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myEndOffset;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TreeRangeReference)) return false;

      final TreeRangeReference that = (TreeRangeReference)o;

      if (myEndOffset != that.myEndOffset) return false;
      if (myStartOffset != that.myStartOffset) return false;
      if (myClass != null ? !myClass.equals(that.myClass) : that.myClass != null) return false;
      if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      int result = myClass != null ? myClass.getName().hashCode() : 0;
      result = 31 * result + myStartOffset; //todo
      result = 31 * result + myEndOffset;
      if (myFile != null) {
        result = 31 * result + myFile.getName().hashCode();
      }

      return result;
    }
  }

  private static class HardReference extends PsiAnchor {
    private final PsiElement myElement;

    private HardReference(final PsiElement element) {
      myElement = element;
    }

    public PsiElement retrieve() {
      return myElement;
    }

    public PsiFile getFile() {
      return myElement.getContainingFile();
    }

    public int getStartOffset() {
      return myElement.getTextRange().getStartOffset();
    }

    public int getEndOffset() {
      return myElement.getTextRange().getEndOffset();
    }


    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof HardReference)) return false;

      final HardReference that = (HardReference)o;

      return myElement.equals(that.myElement);
    }

    public int hashCode() {
      return myElement.hashCode();
    }
  }

  public static class StubIndexReference extends PsiAnchor {
    private final VirtualFile myVirtualFile;
    private final Project myProject;
    private final int myIndex;

    public StubIndexReference(@NotNull PsiFile file, final int index) {
      myVirtualFile = file.getVirtualFile();
      myProject = file.getProject();
      myIndex = index;
    }

    public PsiFile getFile() {
      if (myProject.isDisposed() || !myVirtualFile.isValid()) return null;
      return PsiManager.getInstance(myProject).findFile(myVirtualFile);
    }

    public PsiElement retrieve() {
      return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiElement>() {
        public PsiElement compute() {
          PsiFileWithStubSupport fileImpl = (PsiFileWithStubSupport)getFile();
          if (fileImpl == null) return null;
          StubTree tree = fileImpl.getStubTree();

          boolean foreign = tree == null;
          if (foreign) {
            if (fileImpl instanceof PsiFileImpl) {
              tree = ((PsiFileImpl)fileImpl).calcStubTree();
            }
            else {
              return null;
            }
          }

          StubElement stub = tree.getPlainList().get(myIndex);

          if (foreign) {
            final PsiElement cachedPsi = ((StubBase)stub).getCachedPsi();
            if (cachedPsi != null) return cachedPsi;

            final ASTNode ast = fileImpl.findTreeForStub(tree, stub);
            return ast != null ? ast.getPsi() : null;
          }
          else {
            return stub != null ? stub.getPsi() : null;
          }
        }
      });
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof StubIndexReference)) return false;

      final StubIndexReference that = (StubIndexReference)o;

      return myIndex == that.myIndex && myVirtualFile.equals(that.myVirtualFile);
    }

    @Override
    public int hashCode() {
      return 31 * myVirtualFile.hashCode() + myIndex;
    }

    public int getStartOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null);
      return resolved.getTextRange().getStartOffset();
    }

    public int getEndOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null);
      return resolved.getTextRange().getEndOffset();
    }
  }
}


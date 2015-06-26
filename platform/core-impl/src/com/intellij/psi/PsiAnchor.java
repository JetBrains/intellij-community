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

package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.smartPointers.SelfElementInfo;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

  @NotNull
  public static PsiAnchor create(@NotNull final PsiElement element) {
    if (!element.isValid()) {
      throw new PsiInvalidElementAccessException(element);
    }

    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) return new PsiFileReference(virtualFile, (PsiFile)element);
      return new HardReference(element);
    }
    if (element instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      return new PsiDirectoryReference(virtualFile, element.getProject());
    }

    PsiFile file = element.getContainingFile();
    if (file == null) {
      return new HardReference(element);
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return new HardReference(element);

    PsiAnchor stubRef = createStubReference(element, file);
    if (stubRef != null) return stubRef;

    if (!element.isPhysical() && element instanceof PsiCompiledElement || element instanceof LightElement || element instanceof SyntheticElement) {
      return new HardReference(element);
    }

    TextRange textRange = element.getTextRange();
    if (textRange == null) {
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
    return new TreeRangeReference(file, textRange.getStartOffset(), textRange.getEndOffset(), element.getClass(), lang, virtualFile);
  }

  @Nullable
  public static StubIndexReference createStubReference(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (element instanceof StubBasedPsiElement &&
        element.isPhysical() &&
        (element instanceof PsiCompiledElement || canHaveStub(containingFile))) {
      final StubBasedPsiElement elt = (StubBasedPsiElement)element;
      final IStubElementType elementType = elt.getElementType();
      if (elt.getStub() != null || elementType.shouldCreateStub(element.getNode())) {
        int index = calcStubIndex((StubBasedPsiElement)element);
        if (index != -1) {
          return new StubIndexReference(containingFile, index, containingFile.getLanguage(), elementType);
        }
      }
    }
    return null;
  }

  private static boolean canHaveStub(PsiFile file) {
    if (!(file instanceof PsiFileImpl)) return false;

    VirtualFile vFile = file.getVirtualFile();
    IElementType elementType = ((PsiFileImpl)file).getContentElementType();
    return elementType instanceof IStubFileElementType && vFile != null && ((IStubFileElementType)elementType).shouldBuildStubFor(vFile);
  }

  public static int calcStubIndex(@NotNull StubBasedPsiElement psi) {
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

    return -1; // it is possible via custom stub builder intentionally not producing stubs for stubbed elements
  }

  private static class TreeRangeReference extends PsiAnchor {
    private final VirtualFile myVirtualFile;
    private final Project myProject;
    private final Language myLanguage;
    private final Language myFileLanguage;
    private final int myStartOffset;
    private final int myEndOffset;
    private final Class myClass;

    private TreeRangeReference(@NotNull PsiFile file,
                               int startOffset,
                               int endOffset,
                               @NotNull Class aClass,
                               @NotNull Language language,
                               @NotNull VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
      myProject = file.getProject();
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myClass = aClass;
      myLanguage = language;
      myFileLanguage = file.getLanguage();
    }

    @Override
    @Nullable
    public PsiElement retrieve() {
      PsiFile psiFile = getFile();
      if (psiFile == null || !psiFile.isValid()) return null;
      PsiElement element = psiFile.getViewProvider().findElementAt(myStartOffset, myLanguage);
      if (element == null) return null;

      while  (!element.getClass().equals(myClass) ||
              element.getTextRange().getStartOffset() != myStartOffset ||
              element.getTextRange().getEndOffset() != myEndOffset) {
        element = element.getParent();
        if (element == null || element.getTextRange() == null) return null;
      }

      return element;
    }

    @Override
    @Nullable
    public PsiFile getFile() {
      return SelfElementInfo.restoreFileFromVirtual(myVirtualFile, myProject, myFileLanguage);
    }

    @Override
    public int getStartOffset() {
      return myStartOffset;
    }

    @Override
    public int getEndOffset() {
      return myEndOffset;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TreeRangeReference)) return false;

      final TreeRangeReference that = (TreeRangeReference)o;

      return myEndOffset == that.myEndOffset &&
             myStartOffset == that.myStartOffset &&
             myClass.equals(that.myClass) &&
             myVirtualFile.equals(that.myVirtualFile);
    }

    public int hashCode() {
      int result = myClass.getName().hashCode();
      result = 31 * result + myStartOffset;
      result = 31 * result + myEndOffset;
      result = 31 * result + myVirtualFile.hashCode();

      return result;
    }
  }

  public static class HardReference extends PsiAnchor {
    private final PsiElement myElement;

    public HardReference(final PsiElement element) {
      myElement = element;
    }

    @Override
    public PsiElement retrieve() {
      return myElement;
    }

    @Override
    public PsiFile getFile() {
      return myElement.getContainingFile();
    }

    @Override
    public int getStartOffset() {
      return myElement.getTextRange().getStartOffset();
    }

    @Override
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

  private static class PsiFileReference extends PsiAnchor {
    private final VirtualFile myFile;
    private final Project myProject;
    @NotNull private final Language myLanguage;

    private PsiFileReference(@NotNull VirtualFile file, @NotNull PsiFile psiFile) {
      myFile = file;
      myProject = psiFile.getProject();
      myLanguage = findLanguage(psiFile);
    }

    private static Language findLanguage(PsiFile file) {
      FileViewProvider vp = file.getViewProvider();
      Set<Language> languages = vp.getLanguages();
      for (Language language : languages) {
        if (file.equals(vp.getPsi(language))) {
          return language;
        }
      }
      throw new AssertionError("Non-retrievable file: " + file.getClass() + "; " + file.getLanguage() + "; " + languages);
    }

    @Override
    public PsiElement retrieve() {
      return getFile();
    }

    @Override
    @Nullable
    public PsiFile getFile() {
      return SelfElementInfo.restoreFileFromVirtual(myFile, myProject, myLanguage);
    }

    @Override
    public int getStartOffset() {
      return 0;
    }

    @Override
    public int getEndOffset() {
      return (int)myFile.getLength();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PsiFileReference)) return false;

      PsiFileReference reference = (PsiFileReference)o;

      if (!myFile.equals(reference.myFile)) return false;
      if (!myLanguage.equals(reference.myLanguage)) return false;
      if (!myProject.equals(reference.myProject)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * myFile.hashCode() + myLanguage.hashCode();
    }
  }
  
  private static class PsiDirectoryReference extends PsiAnchor {
    private final VirtualFile myFile;
    private final Project myProject;
    
    private PsiDirectoryReference(@NotNull VirtualFile file, @NotNull Project project) {
      myFile = file;
      myProject = project;
      assert file.isDirectory() : file;
    }

    @Override
    public PsiElement retrieve() {
      return SelfElementInfo.restoreDirectoryFromVirtual(myFile, myProject);
    }

    @Override
    public PsiFile getFile() {
      return null;
    }

    @Override
    public int getStartOffset() {
      return 0;
    }

    @Override
    public int getEndOffset() {
      return -1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PsiDirectoryReference)) return false;

      PsiDirectoryReference reference = (PsiDirectoryReference)o;

      if (!myFile.equals(reference.myFile)) return false;
      if (!myProject.equals(reference.myProject)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }
  }

  @Nullable
  public static PsiElement restoreFromStubIndex(PsiFileWithStubSupport fileImpl,
                                                int index,
                                                @NotNull IStubElementType elementType, boolean throwIfNull) {
    if (fileImpl == null) {
      if (throwIfNull) throw new AssertionError("Null file");
      return null;
    }
    StubTree tree = fileImpl.getStubTree();

    boolean foreign = tree == null;
    if (foreign) {
      if (fileImpl instanceof PsiFileImpl) {
        // Note: as far as this is a realization of StubIndexReference fileImpl#getContentElementType() must be instance of IStubFileElementType
        tree = ((PsiFileImpl)fileImpl).calcStubTree();
      }
      else {
        if (throwIfNull) throw new AssertionError("Not PsiFileImpl: " + fileImpl.getClass());
        return null;
      }
    }

    List<StubElement<?>> list = tree.getPlainList();
    if (index >= list.size()) {
      if (throwIfNull) throw new AssertionError("Too large index: " + index + ">=" + list.size());
      return null;
    }
    StubElement stub = list.get(index);

    if (stub.getStubType() != elementType) {
      if (throwIfNull) throw new AssertionError("Element type mismatch: " + stub.getStubType() + "!=" + elementType);
      return null;
    }

    if (foreign) {
      final PsiElement cachedPsi = ((StubBase)stub).getCachedPsi();
      if (cachedPsi != null) return cachedPsi;

      final ASTNode ast = fileImpl.findTreeForStub(tree, stub);
      if (ast != null) {
        return ast.getPsi();
      }

      if (throwIfNull) throw new AssertionError("No AST for stub");
      return null;
    }
    return stub.getPsi();
  }

  public static class StubIndexReference extends PsiAnchor {
    private final VirtualFile myVirtualFile;
    private final Project myProject;
    private final int myIndex;
    private final Language myLanguage;
    private final IStubElementType myElementType;
    private final int myCreationModCount;

    private StubIndexReference(@NotNull final PsiFile file, final int index, @NotNull Language language, IStubElementType elementType) {
      myLanguage = language;
      myElementType = elementType;
      myVirtualFile = file.getVirtualFile();
      myProject = file.getProject();
      myIndex = index;
      myCreationModCount = (int)file.getManager().getModificationTracker().getModificationCount();
    }

    @Override
    @Nullable
    public PsiFile getFile() {
      if (myProject.isDisposed() || !myVirtualFile.isValid()) {
        return null;
      }
      final PsiFile file = PsiManager.getInstance(myProject).findFile(myVirtualFile);
      if (file == null) {
        return null;
      }
      if (file.getLanguage() == myLanguage) {
        return file;
      }
      return file.getViewProvider().getPsi(myLanguage);
    }

    @Override
    public PsiElement retrieve() {
      return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiElement>() {
        @Override
        public PsiElement compute() {
          return restoreFromStubIndex((PsiFileWithStubSupport)getFile(), myIndex, myElementType, false);
        }
      });
    }

    public String diagnoseNull() {
      try {
        PsiElement element = ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiElement>() {
          @Override
          public PsiElement compute() {
            return restoreFromStubIndex((PsiFileWithStubSupport)getFile(), myIndex, myElementType, true);
          }
        });
        return "No diagnostics, element=" + element + "@" + (element == null ? 0 : System.identityHashCode(element));
      }
      catch (AssertionError e) {
        return e.getMessage() +
               "; current modCount=" + PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount() +
               "; creation modCount=" + myCreationModCount;
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof StubIndexReference)) return false;

      final StubIndexReference that = (StubIndexReference)o;

      return myIndex == that.myIndex &&
             myVirtualFile.equals(that.myVirtualFile) &&
             Comparing.equal(myElementType, that.myElementType) &&
             myLanguage == that.myLanguage;
    }

    @Override
    public int hashCode() {
      return ((31 * myVirtualFile.hashCode() + myIndex) * 31 + (myElementType == null ? 0 : myElementType.hashCode())) * 31 + myLanguage.hashCode();
    }

    @NonNls
    @Override
    public String toString() {
      return "StubIndexReference{" +
             "myVirtualFile=" + myVirtualFile +
             ", myProject=" + myProject +
             ", myIndex=" + myIndex +
             ", myLanguage=" + myLanguage +
             ", myElementType=" + myElementType +
             '}';
    }

    @Override
    public int getStartOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null, "Element type: " + myElementType + "; " + myVirtualFile);
      return resolved.getTextRange().getStartOffset();
    }

    @Override
    public int getEndOffset() {
      final PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null, "Element type: " + myElementType + "; " + myVirtualFile);
      return resolved.getTextRange().getEndOffset();
    }

    public VirtualFile getVirtualFile() {
      return myVirtualFile;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }
  }
}


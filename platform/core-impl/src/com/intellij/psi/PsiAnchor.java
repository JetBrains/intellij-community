// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.model.Pointer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.smartPointers.Identikit;
import com.intellij.psi.impl.smartPointers.SelfElementInfo;
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class PsiAnchor implements Pointer<PsiElement> {

  public abstract @Nullable PsiElement retrieve();
  public abstract PsiFile getFile();
  public abstract int getStartOffset();
  public abstract int getEndOffset();

  @Override
  public @Nullable PsiElement dereference() {
    return retrieve();
  }

  public static @NotNull PsiAnchor create(@NotNull PsiElement element) {
    PsiUtilCore.ensureValid(element);

    PsiAnchor anchor = doCreateAnchor(element);
    if (ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
      PsiElement restored = anchor.retrieve();
      if (!element.equals(restored)) {
        Logger.getInstance(PsiAnchor.class)
          .error("Cannot restore element " + element  + " of " + element.getClass()
                 + " from anchor " + anchor + ", getting " + restored + " instead");
      }
    }
    return anchor;
  }

  private static @NotNull PsiAnchor doCreateAnchor(@NotNull PsiElement element) {
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
    if (virtualFile == null || virtualFile instanceof VirtualFileWindow) return new HardReference(element);

    PsiAnchor stubRef = createStubReference(element, file);
    if (stubRef != null) return stubRef;

    if (!element.isPhysical()) {
      return wrapperOrHardReference(element);
    }

    TextRange textRange = element.getTextRange();
    if (textRange == null) {
      return wrapperOrHardReference(element);
    }

    Language lang = null;
    FileViewProvider viewProvider = file.getViewProvider();
    for (Language l : viewProvider.getLanguages()) {
      if (viewProvider.getPsi(l) == file) {
        lang = l;
        break;
      }
    }

    if (lang == null) {
      return wrapperOrHardReference(element);
    }

    return new TreeRangeReference(file, textRange.getStartOffset(), textRange.getEndOffset(), Identikit.fromPsi(element, lang), virtualFile);
  }

  private static @NotNull PsiAnchor wrapperOrHardReference(@NotNull PsiElement element) {
    for (SmartPointerAnchorProvider provider : SmartPointerAnchorProvider.EP_NAME.getExtensionList()) {
      PsiElement anchorElement = provider.getAnchor(element);
      if (anchorElement != null && anchorElement != element) {
        PsiAnchor wrappedAnchor = create(anchorElement);
        if (!(wrappedAnchor instanceof HardReference)) {
          return new WrappedElementAnchor(provider, wrappedAnchor);
        }
      }
    }
    return new HardReference(element);
  }

  public static @Nullable StubIndexReference createStubReference(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (element instanceof StubBasedPsiElement &&
        element.isPhysical() &&
        (element instanceof PsiCompiledElement || canHaveStub(containingFile))) {
      StubBasedPsiElement<?> elt = (StubBasedPsiElement<?>)element;
      IElementType elementType = elt.getIElementType();
      StubElementFactory<?, ?> factory = StubElementRegistryService.getInstance().getStubFactory(elementType);
      if (factory == null) return null;
      if (elt.getStub() != null || StubElementUtil.shouldCreateStubForPsi(factory, element)) {
        int index = calcStubIndex((StubBasedPsiElement<?>)element);
        if (index != -1) {
          return new StubIndexReference(containingFile, index, containingFile.getLanguage(), elementType);
        }
      }
    }
    return null;
  }

  private static boolean canHaveStub(@NotNull PsiFile file) {
    if (!(file instanceof PsiFileImpl)) return false;

    VirtualFile vFile = file.getVirtualFile();

    LanguageStubDescriptor stubDescriptor = ((PsiFileImpl)file).getStubDescriptor();
    return stubDescriptor != null && vFile != null && stubDescriptor.getStubDefinition().shouldBuildStubFor(vFile);
  }

  public static int calcStubIndex(@NotNull StubBasedPsiElement<?> psi) {
    if (psi instanceof PsiFile) {
      return 0;
    }

    StubElement<?> liveStub = psi instanceof StubBasedPsiElementBase ? ((StubBasedPsiElementBase<?>)psi).getGreenStub() : psi.getStub();
    if (liveStub != null) {
      return ((StubBase<?>)liveStub).getStubId();
    }

    return ((PsiFileImpl)psi.getContainingFile()).calcTreeElement().getStubbedSpine().getStubIndex(psi);
  }

  /**
   * Retrieves a PSI element from anchor or throws {@link PsiInvalidElementAccessException} in case anchor has not survived.
   */
  public @NotNull PsiElement retrieveOrThrow() {
    PsiElement element = retrieve();
    if (element == null) {
      String msg;
      if (this instanceof StubIndexReference) {
        msg = ((StubIndexReference)this).diagnoseNull();
      }
      else {
        msg = "Anchor hasn't survived: " + this;
      }
      throw new PsiInvalidElementAccessException(null, msg);
    }

    return element;
  }

  private static final class TreeRangeReference extends PsiAnchor {
    private final VirtualFile myVirtualFile;
    private final @NotNull CodeInsightContext myContext; // todo IJPL-339 object layout got bigger +8 bytes
    private final Project myProject;
    private final Identikit myInfo;
    private final int myStartOffset;
    private final int myEndOffset;

    private TreeRangeReference(@NotNull PsiFile file,
                               int startOffset,
                               int endOffset,
                               @NotNull Identikit info,
                               @NotNull VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
      myContext = CodeInsightContextUtil.getCodeInsightContext(file);
      myProject = file.getProject();
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myInfo = info;
    }

    @Override
    public @Nullable PsiElement retrieve() {
      PsiFile psiFile = getFile();
      if (psiFile == null || !psiFile.isValid()) return null;

      return myInfo.findPsiElement(psiFile, myStartOffset, myEndOffset);
    }

    @Override
    public @Nullable PsiFile getFile() {
      Language language = myInfo.getFileLanguage();
      if (language == null) return null;
      return SelfElementInfo.restoreFileFromVirtual(myVirtualFile, myContext, myProject, language);
    }

    @Override
    public int getStartOffset() {
      return myStartOffset;
    }

    @Override
    public int getEndOffset() {
      return myEndOffset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TreeRangeReference)) return false;

      TreeRangeReference that = (TreeRangeReference)o;

      return myEndOffset == that.myEndOffset &&
             myStartOffset == that.myStartOffset &&
             myInfo.equals(that.myInfo) &&
             myVirtualFile.equals(that.myVirtualFile);
    }

    @Override
    public int hashCode() {
      int result = myInfo.hashCode();
      result = 31 * result + myStartOffset;
      result = 31 * result + myEndOffset;
      result = 31 * result + myVirtualFile.hashCode();

      return result;
    }
  }

  public static class HardReference extends PsiAnchor {
    private final PsiElement myElement;

    public HardReference(@NotNull PsiElement element) {
      myElement = element;
    }

    @Override
    public PsiElement retrieve() {
      return myElement.isValid() ? myElement : null;
    }

    @Override
    public @NotNull PsiElement retrieveOrThrow() {
      PsiUtilCore.ensureValid(myElement);
      return super.retrieveOrThrow();
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


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof HardReference)) return false;

      HardReference that = (HardReference)o;

      return myElement.equals(that.myElement);
    }

    @Override
    public int hashCode() {
      return myElement.hashCode();
    }
  }

  private static final class PsiFileReference extends PsiAnchor {
    private final VirtualFile myFile;
    private final CodeInsightContext myContext;
    private final Project myProject;
    private final @NotNull Language myLanguage;

    private PsiFileReference(@NotNull VirtualFile file, @NotNull PsiFile psiFile) {
      myFile = file;
      myContext = CodeInsightContextUtil.getCodeInsightContext(psiFile);
      myProject = psiFile.getProject();
      myLanguage = findLanguage(psiFile);
    }

    private static @NotNull Language findLanguage(@NotNull PsiFile file) {
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
    public @Nullable PsiFile getFile() {
      return SelfElementInfo.restoreFileFromVirtual(myFile, myContext, myProject, myLanguage);
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

  private static final class PsiDirectoryReference extends PsiAnchor {
    private final @NotNull VirtualFile myFile;
    private final @NotNull Project myProject;

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

  public static @Nullable PsiElement restoreFromStubIndex(PsiFileWithStubSupport fileImpl,
                                                          int index,
                                                          @NotNull IElementType elementType,
                                                          boolean throwIfNull) {
    if (fileImpl == null) {
      if (throwIfNull) throw new AssertionError("Null file");
      return null;
    }

    if (index == 0) return fileImpl;

    StubbedSpine spine = fileImpl.getStubbedSpine();
    StubBasedPsiElement<?> psi = (StubBasedPsiElement<?>)spine.getStubPsi(index);
    if (psi == null) {
      if (throwIfNull) throw new AssertionError("Too large index: " + index + ">=" + spine.getStubCount());
      return null;
    }

    if (psi.getIElementType() != elementType) {
      if (throwIfNull) throw new AssertionError("Element type mismatch: " + psi.getIElementType() + "!=" + elementType);
      return null;
    }

    return psi;
  }

  public static final class StubIndexReference extends PsiAnchor {
    private final @NotNull VirtualFile myVirtualFile;
    private final @NotNull CodeInsightContext myContext;
    private final @NotNull Project myProject;
    private final int myIndex;
    private final @NotNull Language myLanguage;
    private final @NotNull IElementType myElementType;

    private StubIndexReference(@NotNull PsiFile file,
                               int index,
                               @NotNull Language language,
                               @NotNull IElementType elementType) {
      myLanguage = language;
      myElementType = elementType;
      myVirtualFile = file.getVirtualFile();
      myContext = CodeInsightContextUtil.getCodeInsightContext(file);
      if (file.getViewProvider() instanceof FreeThreadedFileViewProvider) {
        throw new IllegalArgumentException("Must not use StubIndexReference for injected file; take a closer look at HardReference instead");
      }
      myProject = file.getProject();
      myIndex = index;
    }

    @Override
    public @Nullable PsiFile getFile() {
      if (myProject.isDisposed() || !myVirtualFile.isValid()) {
        return null;
      }
      FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(myVirtualFile, myContext);
      PsiFile file = viewProvider == null ? null : viewProvider.getPsi(myLanguage);
      return file instanceof PsiFileWithStubSupport ? file : null;
    }

    @Override
    public PsiElement retrieve() {
      return ReadAction.compute(() -> restoreFromStubIndex((PsiFileWithStubSupport)getFile(), myIndex, myElementType, false));
    }

    public @NotNull @NonNls String diagnoseNull() {
      PsiFile file = ReadAction.compute(this::getFile);
      try {
        PsiElement element = ReadAction.compute(() -> restoreFromStubIndex((PsiFileWithStubSupport)file, myIndex, myElementType, true));
        return "No diagnostics, element=" + element + "@" + (element == null ? 0 : System.identityHashCode(element));
      }
      catch (AssertionError e) {
        String msg = e.getMessage();
        msg += file == null ? "\n no PSI file" : "\n current file stamp=" + (short)file.getModificationStamp();
        Document document = FileDocumentManager.getInstance().getCachedDocument(myVirtualFile);
        if (document != null) {
          msg += "\n committed=" + PsiDocumentManager.getInstance(myProject).isCommitted(document);
          msg += "\n saved=" + !FileDocumentManager.getInstance().isDocumentUnsaved(document);
        }
        return msg;
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof StubIndexReference)) return false;

      StubIndexReference that = (StubIndexReference)o;

      return myIndex == that.myIndex &&
             myVirtualFile.equals(that.myVirtualFile) &&
             Comparing.equal(myElementType, that.myElementType) &&
             myLanguage == that.myLanguage;
    }

    @Override
    public int hashCode() {
      return ((31 * myVirtualFile.hashCode() + myIndex) * 31 + myElementType.hashCode()) * 31 + myLanguage.hashCode();
    }

    @Override
    public @NonNls String toString() {
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
      return getTextRange().getStartOffset();
    }

    @Override
    public int getEndOffset() {
      return getTextRange().getEndOffset();
    }

    private @NotNull TextRange getTextRange() {
      PsiElement resolved = retrieve();
      if (resolved == null) throw new PsiInvalidElementAccessException(null, "Element type: " + myElementType + "; " + myVirtualFile);
      return resolved.getTextRange();
    }

    public @NotNull VirtualFile getVirtualFile() {
      return myVirtualFile;
    }

    public @NotNull Project getProject() {
      return myProject;
    }
  }
}


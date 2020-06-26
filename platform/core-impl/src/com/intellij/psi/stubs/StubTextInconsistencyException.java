// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public final class StubTextInconsistencyException extends RuntimeException implements ExceptionWithAttachments {
  private final String myStubsFromText;
  private final String myStubsFromPsi;
  private final String myFileName;
  private final String myFileText;

  private StubTextInconsistencyException(String message, PsiFile file, List<PsiFileStub> fromText, List<PsiFileStub> fromPsi) {
    super(message);
    myStubsFromText = StringUtil.join(fromText, DebugUtil::stubTreeToString, "\n");
    myStubsFromPsi = StringUtil.join(fromPsi, DebugUtil::stubTreeToString, "\n");
    myFileName = file.getName();
    myFileText = file.getText();
  }

  @NotNull
  public String getStubsFromText() {
    return myStubsFromText;
  }

  @NotNull
  public String getStubsFromPsi() {
    return myStubsFromPsi;
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
    return new Attachment[]{
      new Attachment(myFileName, myFileText),
      new Attachment("stubsRestoredFromText.txt", myStubsFromText),
      new Attachment("stubsFromExistingPsi.txt", myStubsFromPsi)};
  }

  public static void checkStubTextConsistency(@NotNull PsiFile file) throws StubTextInconsistencyException {
    PsiUtilCore.ensureValid(file);

    FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider instanceof FreeThreadedFileViewProvider || viewProvider.getVirtualFile() instanceof LightVirtualFile) return;

    PsiFile bindingRoot = viewProvider.getStubBindingRoot();
    if (!(bindingRoot instanceof PsiFileImpl)) return;

    IStubFileElementType fileElementType = ((PsiFileImpl)bindingRoot).getElementTypeForStubBuilder();
    if (fileElementType == null || !fileElementType.shouldBuildStubFor(viewProvider.getVirtualFile())) return;

    List<PsiFileStub> fromText = restoreStubsFromText(viewProvider);

    List<PsiFileStub> fromPsi = ContainerUtil
      .map(StubTreeBuilder.getStubbedRoots(viewProvider), p -> ((PsiFileImpl)p.getSecond()).calcStubTree().getRoot());

    if (fromPsi.size() != fromText.size()) {
      throw new StubTextInconsistencyException("Inconsistent stub roots: " +
                                               "PSI says it's " + ContainerUtil.map(fromPsi, s -> s.getType()) +
                                               " but re-parsing the text gives " + ContainerUtil.map(fromText, s -> s.getType()),
                                               file, fromText, fromPsi);
    }

    for (int i = 0; i < fromPsi.size(); i++) {
      PsiFileStub psiStub = fromPsi.get(i);
      if (!DebugUtil.stubTreeToString(psiStub).equals(DebugUtil.stubTreeToString(fromText.get(i)))) {
        throw new StubTextInconsistencyException("Stub is inconsistent with text in " + file.getLanguage(),
                                                 file, fromText, fromPsi);
      }
    }
  }

  @NotNull
  private static List<PsiFileStub> restoreStubsFromText(FileViewProvider viewProvider) {
    FileContentImpl fc = new FileContentImpl(viewProvider.getVirtualFile(), viewProvider.getContents(), 0);
    fc.setProject(viewProvider.getManager().getProject());
    PsiFileStubImpl copyTree = (PsiFileStubImpl) StubTreeBuilder.buildStubTree(fc);
    return copyTree == null ? Collections.emptyList() : Arrays.asList(copyTree.getStubRoots());
  }
}

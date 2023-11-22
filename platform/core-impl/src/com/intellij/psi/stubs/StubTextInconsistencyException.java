// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.StubInconsistencyReporter.InconsistencyType;
import com.intellij.psi.stubs.StubInconsistencyReporter.SourceOfCheck;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

  public @NotNull String getStubsFromText() {
    return myStubsFromText;
  }

  public @NotNull String getStubsFromPsi() {
    return myStubsFromPsi;
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
    return new Attachment[]{
      new Attachment(myFileName, myFileText),
      new Attachment("stubsRestoredFromText.txt", myStubsFromText),
      new Attachment("stubsFromExistingPsi.txt", myStubsFromPsi)};
  }

  /**
   * Recommended method for plugins
   */
  @SuppressWarnings("unused")
  public static void checkStubTextConsistency(@NotNull PsiFile file) {
    checkStubTextConsistency(file, SourceOfCheck.Other, null);
  }

  public static void checkStubTextConsistency(@NotNull PsiFile file, @NotNull StubInconsistencyReporter.SourceOfCheck reason) {
    checkStubTextConsistency(file, reason, null);
  }

  /**
   * Parameters reason and enforcedInconsistencyType are used for tracking statistic of the errors' sources.
   * {@link  StubInconsistencyReporter.SourceOfCheck#Other} and {@code null} are recommended values for callers outside IntelliJ repository.
   */
  public static void checkStubTextConsistency(@NotNull PsiFile file,
                                              @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                              @Nullable StubInconsistencyReporter.EnforcedInconsistencyType enforcedInconsistencyType)
    throws StubTextInconsistencyException {
    PsiUtilCore.ensureValid(file);

    FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider instanceof FreeThreadedFileViewProvider || viewProvider.getVirtualFile() instanceof LightVirtualFile) {
      reportInconsistency(file, reason, null, enforcedInconsistencyType);
      return;
    }

    PsiFile bindingRoot = viewProvider.getStubBindingRoot();
    if (!(bindingRoot instanceof PsiFileImpl)) {
      reportInconsistency(file, reason, null, enforcedInconsistencyType);
      return;
    }

    IStubFileElementType fileElementType = ((PsiFileImpl)bindingRoot).getElementTypeForStubBuilder();
    if (fileElementType == null || !fileElementType.shouldBuildStubFor(viewProvider.getVirtualFile())) {
      reportInconsistency(file, reason, null, enforcedInconsistencyType);
      return;
    }

    List<PsiFileStub> fromText = restoreStubsFromText(viewProvider);

    List<PsiFileStub> fromPsi = ContainerUtil
      .map(StubTreeBuilder.getStubbedRoots(viewProvider), p -> ((PsiFileImpl)p.getSecond()).calcStubTree().getRoot());

    if (fromPsi.size() != fromText.size()) {
      reportInconsistency(file, reason, InconsistencyType.DifferentNumberOfPsiTrees, enforcedInconsistencyType);
      throw new StubTextInconsistencyException("Inconsistent stub roots: " +
                                               "PSI says it's " + ContainerUtil.map(fromPsi, s -> s.getType()) +
                                               " but re-parsing the text gives " + ContainerUtil.map(fromText, s -> s.getType()),
                                               file, fromText, fromPsi);
    }

    for (int i = 0; i < fromPsi.size(); i++) {
      PsiFileStub psiStub = fromPsi.get(i);
      if (!DebugUtil.stubTreeToString(psiStub).equals(DebugUtil.stubTreeToString(fromText.get(i)))) {
        reportInconsistency(file, reason, InconsistencyType.MismatchingPsiTree, enforcedInconsistencyType);
        throw new StubTextInconsistencyException("Stub is inconsistent with text in " + file.getLanguage(),
                                                 file, fromText, fromPsi);
      }
    }
    reportInconsistency(file, reason, null, enforcedInconsistencyType);
  }

  private static void reportInconsistency(@NotNull PsiFile file, @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                          @Nullable InconsistencyType inconsistencyType,
                                          @Nullable StubInconsistencyReporter.EnforcedInconsistencyType enforcedInconsistencyType) {
    if (inconsistencyType == null) {
      if (enforcedInconsistencyType != null) {
        StubInconsistencyReporter.getInstance().reportEnforcedStubInconsistency(file.getProject(), reason, enforcedInconsistencyType);
      }
      return;
    }
    StubInconsistencyReporter.getInstance().reportStubInconsistency(file.getProject(), reason, inconsistencyType, enforcedInconsistencyType);
  }

  private static @NotNull List<PsiFileStub> restoreStubsFromText(FileViewProvider viewProvider) {
    Project project = viewProvider.getManager().getProject();
    FileContentImpl fc = (FileContentImpl)FileContentImpl.createByText(viewProvider.getVirtualFile(),
                                                                       viewProvider.getContents(),
                                                                       project);
    fc.setProject(project);
    PsiFileStubImpl copyTree = (PsiFileStubImpl)StubTreeBuilder.buildStubTree(fc);
    return copyTree == null ? Collections.emptyList() : Arrays.asList(copyTree.getStubRoots());
  }
}

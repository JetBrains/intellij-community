// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.stubs.StubIdList;
import com.intellij.psi.stubs.StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource;
import com.intellij.psi.stubs.StubProcessingHelperBase;
import com.intellij.psi.stubs.StubRecoveryExtraStep;
import com.intellij.psi.stubs.StubRecoveryService;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import static com.intellij.openapi.application.ActionsKt.runReadActionBlocking;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.psiFileFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.sourceRootFixture;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/** Checks that a detected stub inconsistency requests the recovery at the detection point. */
@TestApplication
class StubInconsistencyRecoveryTest {
  private record Request(@Nullable Project project, @NotNull VirtualFile file, @NotNull List<StubRecoveryExtraStep> extraSteps) { }

  private final TestFixture<PsiFile> fileFixture = psiFileFixture(
    sourceRootFixture(moduleFixture(projectFixture())), "Test.java", "class Test {}");
  private final List<Request> requests = new ArrayList<>();

  @BeforeEach
  void recordRecoveryRequests(@TestDisposable Disposable disposable) {
    StubRecoveryService recorder = (project, file, extraSteps) -> requests.add(new Request(project, file, List.of(extraSteps)));
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), StubRecoveryService.class, recorder, disposable);
  }

  @Test
  void psiReconciliationFailureRequestsFullRecovery() {
    var file = (PsiFileImpl)fileFixture.get();

    var exception = runReadActionBlocking(() -> StubTreeLoader.getInstance().createCoarseExceptionStubTreeAndIndexDoNotMatch(
      null, file, null, StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation));

    assertThat(exception).isNotNull();
    assertRecovery(file, StubRecoveryExtraStep.DROP_PSI_CACHES, StubRecoveryExtraStep.SAVE_DOCUMENT, StubRecoveryExtraStep.REPARSE_FILE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void diagnosticFailureStillRequestsFullRecovery(boolean completeException) {
    var file = fileFixture.get();
    var diagnosticFailure = new IllegalStateException("The PSI diagnostic failed");

    runReadActionBlocking(() -> {
      var brokenFile = new PsiJavaFileImpl(file.getViewProvider()) {
        @Override
        public int getTextLength() {
          throw diagnosticFailure;
        }
      };
      var loader = StubTreeLoader.getInstance();
      var failure = assertThrows(IllegalStateException.class, () -> {
        if (completeException) {
          throw loader.stubTreeAndIndexDoNotMatch(null, brokenFile, null, StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation);
        }
        else {
          throw loader.createCoarseExceptionStubTreeAndIndexDoNotMatch(
            null, brokenFile, null, StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation);
        }
      });
      assertThat(failure).isSameAs(diagnosticFailure);
      return null;
    });

    assertRecovery(file, StubRecoveryExtraStep.DROP_PSI_CACHES, StubRecoveryExtraStep.SAVE_DOCUMENT, StubRecoveryExtraStep.REPARSE_FILE);
  }

  @Test
  void wrongPsiTypeFromIndexRequestsPsiCacheDrop() {
    var file = (PsiFileImpl)fileFixture.get();

    var exception = runReadActionBlocking(
      () -> StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(null, file, null, StubTreeAndIndexDoNotMatchSource.StubPsiCheck));

    assertThat(exception).hasMessageContaining("PSI and index do not match");
    assertRecovery(file, StubRecoveryExtraStep.DROP_PSI_CACHES);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stubIndexDiagnosticWithoutRecoveryService(boolean completeException) {
    var file = (PsiFileImpl)fileFixture.get();
    var application = ApplicationManager.getApplication();
    var recovery = StubRecoveryService.getInstance();
    var cause = new IllegalStateException("The stub does not match the AST");
    ServiceContainerUtil.unregisterService(application, StubRecoveryService.class);
    try {
      var diagnostic = runReadActionBlocking(() -> {
        var loader = StubTreeLoader.getInstance();
        if (completeException) {
          return loader.stubTreeAndIndexDoNotMatch(null, file, cause, StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation);
        }
        return loader.createCoarseExceptionStubTreeAndIndexDoNotMatch(
          null, file, cause, StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation).createCompleteException();
      });

      assertThat(diagnostic).hasMessageContaining("PSI and index do not match");
      assertThat(diagnostic.getCause()).isSameAs(cause);
      assertThat(assertInstanceOf(ExceptionWithAttachments.class, diagnostic).getAttachments()).anySatisfy(attachment -> {
        assertThat(attachment.getPath()).endsWith("Test.java_file.txt");
        assertThat(attachment.getDisplayText()).isEqualTo("class Test {}");
      });
    }
    finally {
      ServiceContainerUtil.registerServiceInstance(application, StubRecoveryService.class, recovery);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stubTextDiagnosticWithOptionalRecovery(boolean recoveryAvailable) {
    var file = fileFixture.get();
    var application = ApplicationManager.getApplication();
    var recovery = StubRecoveryService.getInstance();
    if (!recoveryAvailable) ServiceContainerUtil.unregisterService(application, StubRecoveryService.class);
    try {
      var diagnostic = runReadActionBlocking(() -> {
        var pusher = requireNonNull(FilePropertyPusher.EP_NAME.findExtension(JavaLanguageLevelPusher.class));
        pusher.getFilePropertyKey().setPersistentValue(file.getVirtualFile(), pusher.getDefaultValue());
        var differentFile = (PsiFileImpl)PsiFileFactory.getInstance(file.getProject())
          .createFileFromText("Different.java", file.getFileType(), "class Different {}");
        var inconsistentStubs = differentFile.calcStubTree();
        var provider = new SingleRootFileViewProvider(file.getManager(), file.getVirtualFile(), false) {
          @Override
          protected @NotNull PsiFile createFile(@NotNull Project project, @NotNull VirtualFile virtualFile, @NotNull FileType fileType) {
            return new PsiJavaFileImpl(this) {
              @Override
              public @NotNull StubTree calcStubTree() {
                return inconsistentStubs;
              }
            };
          }
        };
        var inconsistentFile = (PsiFileImpl)requireNonNull(provider.getPsi(provider.getBaseLanguage()));
        var descriptor = requireNonNull(inconsistentFile.getStubDescriptor());
        assertThat(descriptor.getStubDefinition().shouldBuildStubFor(file.getVirtualFile())).isTrue();
        return assertThrows(StubTextInconsistencyException.class,
                            () -> StubTextInconsistencyException.checkStubTextConsistency(inconsistentFile));
      });

      assertThat(diagnostic).hasMessageContaining("Stub is inconsistent with text");
      assertThat(diagnostic.getStubsFromText()).contains("Test");
      assertThat(diagnostic.getStubsFromPsi()).contains("Different");
      assertThat(diagnostic.getAttachments()).anySatisfy(attachment -> {
        assertThat(attachment.getPath()).isEqualTo("Test.java");
        assertThat(attachment.getDisplayText()).isEqualTo("class Test {}");
      });
      if (recoveryAvailable) {
        assertRecovery(file, StubRecoveryExtraStep.DROP_PSI_CACHES, StubRecoveryExtraStep.REPARSE_FILE);
      }
      else {
        assertThat(requests).isEmpty();
      }
    }
    finally {
      if (!recoveryAvailable) ServiceContainerUtil.registerServiceInstance(application, StubRecoveryService.class, recovery);
    }
  }

  @Test
  void stubHelperMarksFileAndRequestsRecoveryWhenPsiMatchesText() {
    var file = fileFixture.get();
    var problemFiles = new ArrayList<VirtualFile>();
    var errors = new ArrayList<String>();
    var helper = new StubProcessingHelperBase() {
      @Override
      protected void onInternalError(VirtualFile problemFile) {
        problemFiles.add(problemFile);
      }
    };
    var errorProcessor = new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message,
                                               String @NotNull [] details, @Nullable Throwable t) {
        errors.add(message);
        return Action.NONE;
      }
    };

    runReadActionBlocking(() -> {
      assertThat(file.isPhysical()).isTrue();
      assertThat(((PsiFileImpl)file).getStubDescriptor()).isNotNull();
      StubTextInconsistencyException.checkStubTextConsistency(file);
      assertThat(requests).isEmpty();

      LoggedErrorProcessor.executeWith(errorProcessor, () -> {
        // Stub ID zero denotes the file, not a class.
        boolean completed = helper.processStubsInFile(
          file.getProject(), file.getVirtualFile(), new StubIdList(0),
          element -> { fail("The processor must not receive PSI of the wrong type: " + element); return false; },
          null, PsiClass.class, () -> "test the recovery after a PSI type mismatch");
        assertThat(completed).isTrue();
      });
      return null;
    });

    assertThat(errors).singleElement().asString()
      .contains("psiElement is not instance of requiredClass", "PSI and index do not match");
    assertThat(problemFiles).containsExactly(file.getVirtualFile());
    assertRecovery(file, StubRecoveryExtraStep.DROP_PSI_CACHES);
  }

  @ParameterizedTest
  @MethodSource("stubHelperDiagnosticFailures")
  void stubHelperSkipsRecoveryOnCancellation(RuntimeException diagnosticFailure, boolean expectProblemFile) {
    var file = (PsiFileImpl)fileFixture.get();
    var virtualFile = file.getVirtualFile();
    var problemFiles = new ArrayList<VirtualFile>();
    var helper = new StubProcessingHelperBase() {
      @Override
      protected void onInternalError(VirtualFile problemFile) {
        problemFiles.add(problemFile);
      }
    };

    WriteAction.runAndWait(() -> {
      var pusher = requireNonNull(FilePropertyPusher.EP_NAME.findExtension(JavaLanguageLevelPusher.class));
      pusher.getFilePropertyKey().setPersistentValue(virtualFile, pusher.getDefaultValue());
      assertThat(requireNonNull(file.getStubDescriptor()).getStubDefinition().shouldBuildStubFor(virtualFile)).isTrue();
      var stubs = file.calcStubTree();
      var provider = new SingleRootFileViewProvider(file.getManager(), virtualFile, true) {
        @Override
        public @NotNull CharSequence getContents() {
          throw diagnosticFailure;
        }

        @Override
        protected @NotNull PsiFile createFile(@NotNull Project project, @NotNull VirtualFile virtualFile, @NotNull FileType fileType) {
          return new PsiJavaFileImpl(this) {
            @Override
            public @NotNull StubTree getStubTree() {
              return stubs;
            }
          };
        }
      };
      var fileManager = ((PsiManagerEx)file.getManager()).getFileManager();
      fileManager.setViewProvider(virtualFile, provider);
      try {
        var failure = assertThrows(RuntimeException.class, () -> helper.processStubsInFile(
          file.getProject(), virtualFile, new StubIdList(0),
          element -> { fail("The processor must not receive PSI of the wrong type: " + element); return false; },
          null, PsiClass.class, () -> "test a failure in the consistency check"));
        assertThat(failure).isSameAs(diagnosticFailure);
      }
      finally {
        fileManager.setViewProvider(virtualFile, null);
      }
    });

    assertThat(problemFiles).containsExactlyElementsOf(expectProblemFile ? List.of(virtualFile) : List.of());
    assertThat(requests).isEmpty();
  }

  private static Stream<Arguments> stubHelperDiagnosticFailures() {
    return Stream.of(
      Arguments.of(new ProcessCanceledException(), false),
      Arguments.of(new CancellationException(), false),
      Arguments.of(new IllegalStateException("The consistency check failed"), true));
  }

  private void assertRecovery(PsiFile file, StubRecoveryExtraStep... extraSteps) {
    assertThat(requests).containsExactly(new Request(file.getProject(), requireNonNull(file.getVirtualFile()), List.of(extraSteps)));
  }
}

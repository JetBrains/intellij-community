// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesFixture;
import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A batch run is configured with its own profile via {@link GlobalInspectionContextImpl#setExternalProfile}, which is
 * exposed to the running inspections through {@link InspectionProfileWrapper#getCustomInspectionProfileWrapper}.
 * <p>
 * Inspections that read the project profile directly instead would see a different set of enabled tools than the one the
 * run was configured with. This test pins the profile down for both kinds of tools: regular local inspections, which run
 * inside {@code inspectFile()}, and {@link ExternalAnnotatorBatchInspection}s, which run in a separate step.
 */
@TestApplication
public class BatchInspectionProfileVisibilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private final TestFixture<LightJava9ModulesFixture> java9 = lightJava9ModulesFixture(projectFixture);

  @TempDir
  Path tempDir;

  private static final AtomicReference<InspectionProfile> LOCAL_OBSERVED = new AtomicReference<>();
  private static final AtomicReference<InspectionProfile> ANNOTATOR_OBSERVED = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    LOCAL_OBSERVED.set(null);
    ANNOTATOR_OBSERVED.set(null);
  }

  @AfterEach
  void tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    LOCAL_OBSERVED.set(null);
    ANNOTATOR_OBSERVED.set(null);
  }

  @Test
  public void testRunProfileIsVisibleToLocalInspectionsAndExternalAnnotators() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.addFileToProject("Foo.java", """
      class Foo {
          void m() {
              int i = 42;
          }
      }""");

    InspectionProfileImpl runProfile = runOfflineAnalysis();

    InspectionProfile projectProfile = InspectionProjectProfileManager.getInstance(myFixture.getProject()).getCurrentProfile();
    assertNotSame(projectProfile,
                  runProfile, "the run profile must differ from the project profile, otherwise the test proves nothing");

    assertSame(runProfile, LOCAL_OBSERVED.get(), "a local inspection must see the profile the run was configured with");
    assertSame(runProfile,
               ANNOTATOR_OBSERVED.get(), "an external annotator batch inspection must see the profile the run was configured with");
  }

  private @NotNull InspectionProfileImpl runOfflineAnalysis() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    InspectionManager im = InspectionManager.getInstance(myFixture.getProject());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = tempDir;

    GlobalInspectionContextImpl context = (GlobalInspectionContextImpl)im.createNewGlobalContext();

    List<InspectionToolWrapper<?, ?>> tools = getTools();
    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(tools);
    Disposer.register(myFixture.getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl runProfile =
      new InspectionProfileImpl("runProfile", toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    for (InspectionToolWrapper<?, ?> tool : tools) {
      runProfile.enableTool(tool.getShortName(), myFixture.getProject());
    }

    context.setExternalProfile(runProfile);

    ActionUtil.underModalProgress(myFixture.getProject(), "", () -> {
      context.launchInspectionsOffline(scope, outputPath, false, resultFiles);
      return null;
    });
    return runProfile;
  }

  private static @NotNull List<InspectionToolWrapper<?, ?>> getTools() {
    return Arrays.asList(
      new LocalInspectionToolWrapper(new LocalProbeInspection()),
      new LocalInspectionToolWrapper(new ExternalAnnotatorProbeInspection())
    );
  }

  /** Resolves the profile the platform makes visible for {@code file}, or {@code null} if no profile was published. */
  private static @Nullable InspectionProfile visibleProfile(@NotNull PsiFile file) {
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> customizer =
      InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    if (customizer == null) {
      return null;
    }
    InspectionProfile projectProfile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    return customizer.apply(projectProfile).getInspectionProfile();
  }

  private static class LocalProbeInspection extends LocalInspectionTool {
    @Override
    public @NotNull String getShortName() {
      return "LocalProbe";
    }

    @Override
    public @NotNull String getDisplayName() {
      return "Local Probe";
    }

    @Override
    public @NotNull String getGroupDisplayName() {
      return "test";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          LOCAL_OBSERVED.compareAndSet(null, visibleProfile(holder.getFile()));
        }
      };
    }
  }

  private static class ExternalAnnotatorProbeInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection {
    @Override
    public @NotNull String getShortName() {
      return "ExternalAnnotatorProbe";
    }

    @Override
    public @NotNull String getDisplayName() {
      return "External Annotator Probe";
    }

    @Override
    public @NotNull String getGroupDisplayName() {
      return "test";
    }

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                   @NotNull GlobalInspectionContext context,
                                                   @NotNull InspectionManager manager) {
      ANNOTATOR_OBSERVED.compareAndSet(null, visibleProfile(file));
      return ProblemDescriptor.EMPTY_ARRAY;
    }
  }
}

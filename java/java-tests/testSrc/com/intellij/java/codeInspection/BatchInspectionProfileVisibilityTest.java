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
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A batch run is configured with its own profile via {@link GlobalInspectionContextImpl#setExternalProfile}, which is
 * exposed to the running inspections through {@link InspectionProfileWrapper#getCustomInspectionProfileWrapper}.
 * <p>
 * Inspections that read the project profile directly instead would see a different set of enabled tools than the one the
 * run was configured with. This test pins the profile down for both kinds of tools: regular local inspections, which run
 * inside {@code inspectFile()}, and {@link ExternalAnnotatorBatchInspection}s, which run in a separate step.
 */
public class BatchInspectionProfileVisibilityTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  private static final AtomicReference<InspectionProfile> LOCAL_OBSERVED = new AtomicReference<>();
  private static final AtomicReference<InspectionProfile> ANNOTATOR_OBSERVED = new AtomicReference<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    LOCAL_OBSERVED.set(null);
    ANNOTATOR_OBSERVED.set(null);
  }

  @Override
  public void tearDown() {
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      LOCAL_OBSERVED.set(null);
      ANNOTATOR_OBSERVED.set(null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testRunProfileIsVisibleToLocalInspectionsAndExternalAnnotators() throws Exception {
    myFixture.addFileToProject("Foo.java", """
      class Foo {
          void m() {
              int i = 42;
          }
      }""");

    InspectionProfileImpl runProfile = runOfflineAnalysis();

    InspectionProfile projectProfile = InspectionProjectProfileManager.getInstance(getProject()).getCurrentProfile();
    assertNotSame("the run profile must differ from the project profile, otherwise the test proves nothing",
                  projectProfile, runProfile);

    assertSame("a local inspection must see the profile the run was configured with", runProfile, LOCAL_OBSERVED.get());
    assertSame("an external annotator batch inspection must see the profile the run was configured with",
               runProfile, ANNOTATOR_OBSERVED.get());
  }

  private @NotNull InspectionProfileImpl runOfflineAnalysis() throws Exception {
    InspectionManager im = InspectionManager.getInstance(getProject());
    AnalysisScope scope = new AnalysisScope(getProject());
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = FileUtil.createTempDirectory("inspection", "results").toPath();

    GlobalInspectionContextImpl context = (GlobalInspectionContextImpl)im.createNewGlobalContext();

    List<InspectionToolWrapper<?, ?>> tools = getTools();
    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(tools);
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl runProfile =
      new InspectionProfileImpl("runProfile", toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    for (InspectionToolWrapper<?, ?> tool : tools) {
      runProfile.enableTool(tool.getShortName(), getProject());
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

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.psi.*;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GlobalInspectionContextTest extends JavaCodeInsightTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    ThreadingAssertions.assertEventDispatchThread();
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());
  }

  @Override
  public void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/globalContext/";
  }

  public void testProblemDuplication() throws Exception {
    String shortName = new VisibilityInspection().getShortName();
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    InspectionsKt.disableAllTools(profile);
    profile.enableTool(shortName, getProject());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    context.setExternalProfile(profile);
    configureByFile("Foo.java");

    AnalysisScope scope = new AnalysisScope(getFile());
    context.doInspections(scope);
    UIUtil.dispatchAllInvocationEvents(); // wait for launchInspections in invoke later

    Tools tools = context.getTools().get(shortName);
    GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    assertEquals(1, presentation.getProblemDescriptors().size());

    context.doInspections(scope);
    UIUtil.dispatchAllInvocationEvents(); // wait for launchInspections in invoke later

    tools = context.getTools().get(shortName);
    toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
    presentation = context.getPresentation(toolWrapper);
    assertEquals(1, presentation.getProblemDescriptors().size());
  }

  public void testBatchInspectionMustBeRunUnderDaemonProgressIndicatorToAvoidSpammingStatusBarWithIrrelevantMessages() throws Throwable {
    AtomicBoolean run = new AtomicBoolean();
    AtomicReference<Throwable> throwable = new AtomicReference<>();
    LocalInspectionTool tool = new LocalInspectionTool() {
      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return "fegna2";
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public String getShortName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
          @Override
          public void visitFile(@NotNull PsiFile psiFile) {
            run.set(true);
            ProgressIndicator indicator = ProgressWrapper.unwrapAll(ProgressManager.getGlobalProgressIndicator());
            if (!(indicator instanceof DaemonProgressIndicator)) {
              throwable.set(new IllegalStateException("expected DaemonProgressIndicator but got: " + indicator +" "+indicator.getClass()));
            }
          }
        };
      }
    };
    InspectionProfileImpl profile = InspectionsKt.configureInspections(new InspectionProfileEntry[]{tool}, getProject(), getTestRootDisposable());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    context.setExternalProfile(profile);
    configureByText(PlainTextFileType.INSTANCE, "blah");

    AnalysisScope scope = new AnalysisScope(getFile());
    context.doInspections(scope);
    UIUtil.dispatchAllInvocationEvents(); // wait for launchInspections in invoke later

    assertTrue(run.get());
    if (throwable.get() != null) throw throwable.get();
  }

  public void testRunInspectionContext() {
    InspectionProfile profile = new InspectionProfileImpl("foo");
    List<InspectionToolWrapper<?, ?>> tools = profile.getInspectionTools(null);
    PsiFile file = createDummyFile("xx.txt", "xxx");
    for (InspectionToolWrapper<?, ?> toolWrapper : tools) {
      if (!toolWrapper.isEnabledByDefault()) {
        InspectionManagerEx instance = (InspectionManagerEx)InspectionManager.getInstance(myProject);
        GlobalInspectionContextImpl context = RunInspectionIntention.createContext(toolWrapper, instance, file);
        context.initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        assertEquals(1, context.getTools().size());
        return;
      }
    }
    fail("No disabled tools found: " + tools);
  }

  public void testJavaMethodExternalization() throws Exception {
    PsiFile file = createFile("Foo.java", """
      public class Foo {
          <T> void foo(T t) {
          }
      }""");
    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    context.setCurrentScope(new AnalysisScope(file));
    context.initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    PsiClass[] classes = ((PsiClassOwner)file).getClasses();
    PsiClass fooClass = classes[0];
    PsiMethod fooMethod = fooClass.findMethodsByName("foo", false)[0];
    RefElement refMethod = context.getRefManager().getReference(fooMethod);
    String externalName = refMethod.getExternalName();
    PsiMethod deserialized = RefMethodImpl.findPsiMethod(fooMethod.getManager(), externalName);
    assertEquals(deserialized, fooMethod);
  }

  public void testGlobalSimpleInspectionGetsInterruptedOnWriteActionStart() {
    AtomicBoolean inspectionStarted = new AtomicBoolean();
    AtomicBoolean doRunInspection = new AtomicBoolean(true);
    GlobalSimpleInspectionTool myTool = new GlobalSimpleInspectionTool() {
      @Override
      public void checkFile(@NotNull PsiFile psiFile,
                            @NotNull InspectionManager manager,
                            @NotNull ProblemsHolder problemsHolder,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
        inspectionStarted.set(true);
        while (doRunInspection.get()) {
          ProgressManager.checkCanceled();
        }
        problemsHolder.registerProblem(manager.createProblemDescriptor(psiFile, "Finished: " + getShortName(), (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true));
      }

      @NotNull
      @Override
      public String getShortName() {
        return "myTool";
      }

      @NotNull
      @Override
      public String getDisplayName() {
        return "A"+getShortName();
      }
    };
    String shortName = myTool.getShortName();
    InspectionProfileImpl profile = new InspectionProfileImpl("myTestProfile:"+getTestName(false));
    InspectionsKt.disableAllTools(profile);
    profile.addTool(getProject(), new GlobalInspectionToolWrapper(myTool), null);
    profile.enableTool(shortName, getProject());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    context.setExternalProfile(profile);
    @Language("JAVA")
    String text = "class Foo {}";
    configureByText(JavaFileType.INSTANCE, text);

    AnalysisScope scope = new AnalysisScope(getFile());
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!inspectionStarted.get()) {
          SwingUtilities.invokeLater(this);
          return;
        }

        inspectionStarted.set(false);
        WriteAction.run(() -> { });

        // have to restart
        while (!inspectionStarted.get()) {
          UIUtil.dispatchAllInvocationEvents();
        }

        doRunInspection.set(false);
      }
    });

    context.doInspections(scope);

    UIUtil.dispatchAllInvocationEvents();

    Tools tools = context.getTools().get(shortName);
    GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    CommonProblemDescriptor descriptor = assertOneElement(presentation.getProblemDescriptors());
    assertEquals("Finished: "+shortName, descriptor.getDescriptionTemplate());
  }
}

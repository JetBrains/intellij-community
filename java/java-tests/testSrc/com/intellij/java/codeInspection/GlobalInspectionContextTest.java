/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefFile;
import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.idea.Bombed;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalInspectionContextTest extends JavaCodeInsightTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
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

  public void testRunInspectionContext() {
    InspectionProfile profile = new InspectionProfileImpl("foo");
    InspectionToolWrapper[] tools = profile.getInspectionTools(null);
    PsiFile file = createDummyFile("xx.txt", "xxx");
    for (InspectionToolWrapper toolWrapper : tools) {
      if (!toolWrapper.isEnabledByDefault()) {
        InspectionManagerEx instance = (InspectionManagerEx)InspectionManager.getInstance(myProject);
        GlobalInspectionContextImpl context = RunInspectionIntention.createContext(toolWrapper, instance, file);
        context.initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        assertEquals(1, context.getTools().size());
        return;
      }
    }
    fail("No disabled tools found: " + Arrays.asList(tools));
  }

  public void testJavaMethodExternalization() throws Exception {
    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    PsiFile file = createFile("Foo.java", "public class Foo {\n" +
                                          "    <T> void foo(T t) {\n" +
                                          "    }\n" +
                                          "}");
    PsiClass[] classes = ((PsiClassOwner)file).getClasses();
    PsiClass fooClass = classes[0];
    PsiMethod fooMethod = fooClass.findMethodsByName("foo", false)[0];
    RefElement refMethod = context.getRefManager().getReference(fooMethod);
    String externalName = refMethod.getExternalName();
    PsiMethod deserialized = RefMethodImpl.findPsiMethod(fooMethod.getManager(), externalName);
    assertEquals(deserialized, fooMethod);
  }

  // GlobalInspections don't interrupted on write action yet because it's hard to come up with the way to restart em
  @Bombed(year = 2020, month = Calendar.APRIL, day = 1, user="cdr")
  public void testGlobalInspectionGetsInterruptedOnWriteActionStart() {
    AtomicBoolean inspectionStarted = new AtomicBoolean();
    AtomicBoolean doRunInspection = new AtomicBoolean(true);
    GlobalJavaBatchInspectionTool myTool = new GlobalJavaBatchInspectionTool() {
      @Override
      public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                    @NotNull AnalysisScope scope,
                                                    @NotNull InspectionManager manager,
                                                    @NotNull GlobalInspectionContext globalContext,
                                                    @NotNull ProblemDescriptionsProcessor processor) {
        if (refEntity instanceof RefFile) {
          inspectionStarted.set(true);
          while (doRunInspection.get()) {
            ProgressManager.checkCanceled();
          }
          return new CommonProblemDescriptor[]{ manager.createProblemDescriptor("Finished: "+getShortName()) };
        }
        return CommonProblemDescriptor.EMPTY_ARRAY;
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

  public void testGlobalSimpleInspectionGetsInterruptedOnWriteActionStart() {
    AtomicBoolean inspectionStarted = new AtomicBoolean();
    AtomicBoolean doRunInspection = new AtomicBoolean(true);
    GlobalSimpleInspectionTool myTool = new GlobalSimpleInspectionTool() {
      @Override
      public void checkFile(@NotNull PsiFile file,
                            @NotNull InspectionManager manager,
                            @NotNull ProblemsHolder problemsHolder,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
        inspectionStarted.set(true);
        while (doRunInspection.get()) {
          ProgressManager.checkCanceled();
        }
        problemsHolder.registerProblem(manager.createProblemDescriptor(file, "Finished: "+getShortName(), (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true));
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

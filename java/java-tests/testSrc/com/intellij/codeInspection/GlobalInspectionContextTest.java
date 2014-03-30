/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 *         Date: 5/24/12
 */
public class GlobalInspectionContextTest extends CodeInsightTestCase {

  public void testProblemDuplication() throws Exception {
    String shortName = new VisibilityInspection().getShortName();
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    profile.disableAllTools(getProject());
    profile.enableTool(shortName, getProject());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext(false);
    context.setExternalProfile(profile);
    configureByFile("Foo.java");

    AnalysisScope scope = new AnalysisScope(getFile());
    context.doInspections(scope);

    Tools tools = context.getTools().get(shortName);
    GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
    assertEquals(1, presentation.getProblemDescriptors().size());

    context.doInspections(scope);
    tools = context.getTools().get(shortName);
    toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
    presentation = context.getPresentation(toolWrapper);
    assertEquals(1, presentation.getProblemDescriptors().size());
  }

  public void testRunInspectionContext() throws Exception {
    InspectionProfile profile = new InspectionProfileImpl("foo");
    InspectionToolWrapper[] tools = profile.getInspectionTools(null);
    PsiFile file = createDummyFile("xx.txt", "xxx");
    for (InspectionToolWrapper toolWrapper : tools) {
      if (!toolWrapper.isEnabledByDefault()) {
        InspectionManagerEx instance = (InspectionManagerEx)InspectionManager.getInstance(myProject);
        GlobalInspectionContextImpl context = RunInspectionIntention.createContext(toolWrapper, instance, file);
        context.initializeTools(new ArrayList<Tools>(), new ArrayList<Tools>(), new ArrayList<Tools>());
        assertEquals(1, context.getTools().size());
        return;
      }
    }
    fail("No disabled tools found: " + Arrays.asList(tools));
  }

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

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/globalContext/";
  }
}

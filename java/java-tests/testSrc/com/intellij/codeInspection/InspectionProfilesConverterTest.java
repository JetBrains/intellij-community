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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;

import java.io.File;

/**
 * @author anna
 * Date: 13-Apr-2009
 */
public class InspectionProfilesConverterTest extends LightIdeaTestCase {
  public void testOptions() throws Exception {
    doTest("options");
  }

  public void testScope() throws Exception {
    doTest("scope");
  }

  private static void doTest(final String dirName) throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    try {
      final String relativePath = "/inspection/converter/";
      final File projectFile = new File(JavaTestUtil.getJavaTestDataPath() + relativePath + dirName + "/options.ipr");
      for (Element element : JDOMUtil.loadDocument(projectFile).getRootElement().getChildren("component")) {
        if (Comparing.strEqual(element.getAttributeValue("name"), "InspectionProjectProfileManager")) {
          final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
          profileManager.loadState(element);

          Element configElement = profileManager.getState();
          final File file = new File(JavaTestUtil.getJavaTestDataPath() + relativePath + dirName + "/options.after.xml");
          PlatformTestUtil.assertElementsEqual(JDOMUtil.loadDocument(file).getRootElement(), configElement);
          break;
        }
      }
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }
}
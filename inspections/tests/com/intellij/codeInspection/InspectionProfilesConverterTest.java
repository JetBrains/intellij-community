/*
 * User: anna
 * Date: 13-Apr-2009
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.util.List;

public class InspectionProfilesConverterTest extends LightIdeaTestCase{
  public void testOptions() throws Exception {
    try {
      final List children =
        JDOMUtil.loadDocument(new File(PathManagerEx.getTestDataPath() + "/inspection/converter/options/options.ipr")).getRootElement()
          .getChildren("component");

      for (Object child : children) {
        final Element element = (Element)child;
        if (Comparing.strEqual(element.getAttributeValue("name"), "InspectionProjectProfileManager")) {
          final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
          InspectionProfileImpl.INIT_INSPECTIONS = true;
          profileManager.readExternal(element);

          final Element confElement = new Element("config");
          profileManager.writeExternal(confElement);
          assertTrue(new String(JDOMUtil.printDocument(new Document(confElement), "\n")),
                     JDOMUtil.areElementsEqual(confElement, JDOMUtil.loadDocument(new File(PathManagerEx.getTestDataPath() + "/inspection/converter/options/options.after.xml")).getRootElement()));
          break;
        }
      }
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }
}
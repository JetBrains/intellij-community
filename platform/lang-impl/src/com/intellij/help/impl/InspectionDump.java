/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.help.impl;

import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.TimeoutUtil;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class InspectionDump implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "inspection-dump";
  }

  @Override
  public void premain(String[] args) {

  }

  @Override
  public void main(String[] args) {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.newDocument();
      Element inspections = document.createElement("Inspections");
      document.appendChild(inspections);
      List<InspectionToolWrapper> tools = InspectionToolRegistrar.getInstance().createTools();
      for (InspectionToolWrapper tool : tools) {
        Element inspection = document.createElement("Inspection");
        inspection.setAttribute("group", tool.getGroupDisplayName());
        inspection.setAttribute("name", tool.getDisplayName());
        inspection.setAttribute("level", tool.getDefaultLevel().getName());
        if (tool.getLanguage() != null) {
          inspection.setAttribute("language", tool.getLanguage());
        }
        Element description = document.createElement("description");
        CDATASection descriptionSection = document.createCDATASection(escapeCDATA(tool.loadDescription()));
        description.appendChild(descriptionSection);
        inspection.appendChild(description);
        inspections.appendChild(inspection);
      }

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      DOMSource source = new DOMSource(document);
      final String path = args.length == 2 ? args[1] : PathManager.getHomePath() + File.separator + "AllInspections.xml";
      StreamResult console = new StreamResult(new File(path));
      transformer.transform(source, console);

      System.exit(0);
    }
    catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    catch (TransformerConfigurationException e) {
      e.printStackTrace();
    }
    catch (TransformerException e) {
      e.printStackTrace();
    }
  }

  private static String escapeCDATA(String cData) {
    return cData.replaceAll("\\]", "&#x005D;").replaceAll("\\[", "&#x005B;");
  }
}

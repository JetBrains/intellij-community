/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.tools;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;


class ToolsProcessor extends BaseSchemeProcessor<ToolsGroup> {
  @NonNls private static final String TOOL_SET = "toolSet";
  @NonNls private static final String TOOL = "tool";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String NAME = ATTRIBUTE_NAME;
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String SHOW_IN_MAIN_MENU = "showInMainMenu";
  @NonNls private static final String SHOW_IN_EDITOR = "showInEditor";
  @NonNls private static final String SHOW_IN_PROJECT = "showInProject";
  @NonNls private static final String SHOW_IN_SEARCH_POPUP = "showInSearchPopup";
  @NonNls private static final String DISABLED = "disabled";
  @NonNls private static final String USE_CONSOLE = "useConsole";
  @NonNls private static final String SYNCHRONIZE_AFTER_EXECUTION = "synchronizeAfterRun";
  @NonNls private static final String EXEC = "exec";
  @NonNls private static final String WORKING_DIRECTORY = "WORKING_DIRECTORY";
  @NonNls private static final String COMMAND = "COMMAND";
  @NonNls private static final String PARAMETERS = "PARAMETERS";
  @NonNls private static final String FILTER = "filter";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";

  @NonNls private static final String APPLICATION_HOME_MACRO = "$APPLICATION_HOME_DIR$";

  public ToolsGroup readScheme(final Document document) throws InvalidDataException, IOException, JDOMException {
    Element root = document.getRootElement();
    if (root == null || !TOOL_SET.equals(root.getName())){
      throw new InvalidDataException();
    }

    String groupName = root.getAttributeValue(ATTRIBUTE_NAME);
    ToolsGroup result = new ToolsGroup(groupName);

    for (final Object o : root.getChildren(TOOL)) {
      Element element = (Element)o;

      Tool tool = new Tool();
      tool.setName(ToolManager.convertString(element.getAttributeValue(NAME)));
      tool.setDescription(ToolManager.convertString(element.getAttributeValue(DESCRIPTION)));
      tool.setShownInMainMenu(Boolean.valueOf(element.getAttributeValue(SHOW_IN_MAIN_MENU)).booleanValue());
      tool.setShownInEditor(Boolean.valueOf(element.getAttributeValue(SHOW_IN_EDITOR)).booleanValue());
      tool.setShownInProjectViews(Boolean.valueOf(element.getAttributeValue(SHOW_IN_PROJECT)).booleanValue());
      tool.setShownInSearchResultsPopup(Boolean.valueOf(element.getAttributeValue(SHOW_IN_SEARCH_POPUP)).booleanValue());
      tool.setEnabled(!Boolean.valueOf(element.getAttributeValue(DISABLED)).booleanValue());
      tool.setUseConsole(Boolean.valueOf(element.getAttributeValue(USE_CONSOLE)).booleanValue());
      tool.setFilesSynchronizedAfterRun(Boolean.valueOf(element.getAttributeValue(SYNCHRONIZE_AFTER_EXECUTION)).booleanValue());

      Element exec = element.getChild(EXEC);
      if (exec != null) {
        for (final Object o1 : exec.getChildren(ELEMENT_OPTION)) {
          Element optionElement = (Element)o1;

          String name = optionElement.getAttributeValue(ATTRIBUTE_NAME);
          String value = optionElement.getAttributeValue(ATTRIBUTE_VALUE);

          if (WORKING_DIRECTORY.equals(name)) {
            if (value != null) {
              String appHome = PathManager.getHomePath().replace(File.separatorChar, '/');
              tool.setWorkingDirectory(StringUtil.replace(value, APPLICATION_HOME_MACRO, appHome));
            }
          }
          if (COMMAND.equals(name)) {
            tool.setProgram(ToolManager.convertString(value));
          }
          if (PARAMETERS.equals(name)) {
            tool.setParameters(ToolManager.convertString(value));
          }
        }
      }

      for (final Object o2 : element.getChildren(FILTER)) {
        Element childNode = (Element)o2;

        FilterInfo filterInfo = new FilterInfo();
        filterInfo.readExternal(childNode);
        tool.addOutputFilter(filterInfo);
      }

      tool.setGroup(groupName);
      result.addElement(tool);
    }

    return result;

  }

  public Document writeScheme(final ToolsGroup scheme) throws WriteExternalException {
    Element groupElement = new Element(TOOL_SET);
    if (scheme.getName() != null) {
      groupElement.setAttribute(ATTRIBUTE_NAME, scheme.getName());
    }

    for (Tool tool : scheme.getElements()) {
      saveTool(tool, groupElement);
    }

    return new Document(groupElement);

  }

  public boolean shouldBeSaved(final ToolsGroup scheme) {
    return true;
  }

  private void saveTool(Tool tool, Element groupElement) {
    Element element = new Element(TOOL);
    if (tool.getName() != null) {
      element.setAttribute(NAME, tool.getName());
    }
    if (tool.getDescription() != null) {
      element.setAttribute(DESCRIPTION, tool.getDescription());
    }

    element.setAttribute(SHOW_IN_MAIN_MENU, Boolean.toString(tool.isShownInMainMenu()));
    element.setAttribute(SHOW_IN_EDITOR, Boolean.toString(tool.isShownInEditor()));
    element.setAttribute(SHOW_IN_PROJECT, Boolean.toString(tool.isShownInProjectViews()));
    element.setAttribute(SHOW_IN_SEARCH_POPUP, Boolean.toString(tool.isShownInSearchResultsPopup()));
    element.setAttribute(DISABLED, Boolean.toString(!tool.isEnabled()));
    element.setAttribute(USE_CONSOLE, Boolean.toString(tool.isUseConsole()));
    element.setAttribute(SYNCHRONIZE_AFTER_EXECUTION, Boolean.toString(tool.synchronizeAfterExecution()));

    Element taskElement = new Element(EXEC);

    Element option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, COMMAND);
    if (tool.getProgram() != null ) {
      option.setAttribute(ATTRIBUTE_VALUE, tool.getProgram());
    }

    option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, PARAMETERS);
    if (tool.getParameters() != null ) {
      option.setAttribute(ATTRIBUTE_VALUE, tool.getParameters());
    }

    option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, WORKING_DIRECTORY);
    if (tool.getWorkingDirectory() != null ) {
      String appHome = PathManager.getHomePath().replace(File.separatorChar, '/');
      option.setAttribute(ATTRIBUTE_VALUE, StringUtil.replace(tool.getWorkingDirectory(), appHome, APPLICATION_HOME_MACRO));
    }

    element.addContent(taskElement);

    FilterInfo[] filters = tool.getOutputFilters();
    for (FilterInfo filter : filters) {
      Element filterElement = new Element(FILTER);
      filter.writeExternal(filterElement);
      element.addContent(filterElement);
    }

    groupElement.addContent(element);
  }



}

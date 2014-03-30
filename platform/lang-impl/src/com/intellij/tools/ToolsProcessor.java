/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;


abstract public class ToolsProcessor<T extends Tool> extends BaseSchemeProcessor<ToolsGroup<T>> {
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
  @NonNls private static final String SHOW_CONSOLE_ON_STDOUT = "showConsoleOnStdOut";
  @NonNls private static final String SHOW_CONSOLE_ON_STDERR = "showConsoleOnStdErr";
  @NonNls private static final String SYNCHRONIZE_AFTER_EXECUTION = "synchronizeAfterRun";
  @NonNls private static final String EXEC = "exec";
  @NonNls private static final String WORKING_DIRECTORY = "WORKING_DIRECTORY";
  @NonNls private static final String COMMAND = "COMMAND";
  @NonNls private static final String PARAMETERS = "PARAMETERS";
  @NonNls private static final String FILTER = "filter";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";

  @NonNls private static final String APPLICATION_HOME_MACRO = "$APPLICATION_HOME_DIR$";

  @Override
  public ToolsGroup<T> readScheme(@NotNull final Document document) throws InvalidDataException, IOException, JDOMException {
    Element root = document.getRootElement();
    if (root == null || !TOOL_SET.equals(root.getName())) {
      throw new InvalidDataException();
    }

    String groupName = root.getAttributeValue(ATTRIBUTE_NAME);
    ToolsGroup<T> result = createToolsGroup(groupName);

    final PathMacroManager macroManager = PathMacroManager.getInstance(ApplicationManager.getApplication());

    for (final Object o : root.getChildren(TOOL)) {
      Element element = (Element)o;

      T tool = createTool();

      readToolAttributes(element, tool);

      Element exec = element.getChild(EXEC);
      if (exec != null) {
        for (final Object o1 : exec.getChildren(ELEMENT_OPTION)) {
          Element optionElement = (Element)o1;

          String name = optionElement.getAttributeValue(ATTRIBUTE_NAME);
          String value = optionElement.getAttributeValue(ATTRIBUTE_VALUE);

          if (WORKING_DIRECTORY.equals(name)) {
            if (value != null) {
              final String replace = macroManager.expandPath(value).replace('/', File.separatorChar);
              tool.setWorkingDirectory(replace);
            }
          }
          if (COMMAND.equals(name)) {
            tool.setProgram(macroManager.expandPath(ToolManager.convertString(value)));
          }
          if (PARAMETERS.equals(name)) {
            tool.setParameters(macroManager.expandPath(ToolManager.convertString(value)));
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

  protected void readToolAttributes(Element element, T tool) {
    tool.setName(ToolManager.convertString(element.getAttributeValue(NAME)));
    tool.setDescription(ToolManager.convertString(element.getAttributeValue(DESCRIPTION)));
    tool.setShownInMainMenu(Boolean.valueOf(element.getAttributeValue(SHOW_IN_MAIN_MENU)).booleanValue());
    tool.setShownInEditor(Boolean.valueOf(element.getAttributeValue(SHOW_IN_EDITOR)).booleanValue());
    tool.setShownInProjectViews(Boolean.valueOf(element.getAttributeValue(SHOW_IN_PROJECT)).booleanValue());
    tool.setShownInSearchResultsPopup(Boolean.valueOf(element.getAttributeValue(SHOW_IN_SEARCH_POPUP)).booleanValue());
    tool.setEnabled(!Boolean.valueOf(element.getAttributeValue(DISABLED)).booleanValue());
    tool.setUseConsole(Boolean.valueOf(element.getAttributeValue(USE_CONSOLE)).booleanValue());
    tool.setShowConsoleOnStdOut(Boolean.valueOf(element.getAttributeValue(SHOW_CONSOLE_ON_STDOUT)).booleanValue());
    tool.setShowConsoleOnStdErr(Boolean.valueOf(element.getAttributeValue(SHOW_CONSOLE_ON_STDERR)).booleanValue());
    tool.setFilesSynchronizedAfterRun(Boolean.valueOf(element.getAttributeValue(SYNCHRONIZE_AFTER_EXECUTION)).booleanValue());
  }

  protected abstract ToolsGroup<T> createToolsGroup(String groupName);

  protected abstract T createTool();

  @Override
  public Document writeScheme(@NotNull final ToolsGroup<T> scheme) throws WriteExternalException {
    Element groupElement = new Element(TOOL_SET);
    if (scheme.getName() != null) {
      groupElement.setAttribute(ATTRIBUTE_NAME, scheme.getName());
    }

    for (T tool : scheme.getElements()) {
      saveTool(tool, groupElement);
    }

    return new Document(groupElement);
  }

  @Override
  public boolean shouldBeSaved(@NotNull final ToolsGroup scheme) {
    return true;
  }

  private void saveTool(T tool, Element groupElement) {
    Element element = new Element(TOOL);
    if (tool.getName() != null) {
      element.setAttribute(NAME, tool.getName());
    }
    if (tool.getDescription() != null) {
      element.setAttribute(DESCRIPTION, tool.getDescription());
    }

    saveToolAttributes(tool, element);

    Element taskElement = new Element(EXEC);

    final PathMacroManager macroManager = PathMacroManager.getInstance(ApplicationManager.getApplication());

    Element option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, COMMAND);
    if (tool.getProgram() != null) {
      option.setAttribute(ATTRIBUTE_VALUE, macroManager.collapsePath(tool.getProgram()));
    }

    option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, PARAMETERS);
    if (tool.getParameters() != null) {
      option.setAttribute(ATTRIBUTE_VALUE, macroManager.collapsePath(tool.getParameters()));
    }

    option = new Element(ELEMENT_OPTION);
    taskElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, WORKING_DIRECTORY);
    if (tool.getWorkingDirectory() != null) {
      option.setAttribute(ATTRIBUTE_VALUE, macroManager.collapsePath(tool.getWorkingDirectory()).replace(File.separatorChar, '/'));
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

  protected void saveToolAttributes(T tool, Element element) {
    element.setAttribute(SHOW_IN_MAIN_MENU, Boolean.toString(tool.isShownInMainMenu()));
    element.setAttribute(SHOW_IN_EDITOR, Boolean.toString(tool.isShownInEditor()));
    element.setAttribute(SHOW_IN_PROJECT, Boolean.toString(tool.isShownInProjectViews()));
    element.setAttribute(SHOW_IN_SEARCH_POPUP, Boolean.toString(tool.isShownInSearchResultsPopup()));
    element.setAttribute(DISABLED, Boolean.toString(!tool.isEnabled()));
    element.setAttribute(USE_CONSOLE, Boolean.toString(tool.isUseConsole()));
    element.setAttribute(SHOW_CONSOLE_ON_STDOUT, Boolean.toString(tool.isShowConsoleOnStdOut()));
    element.setAttribute(SHOW_CONSOLE_ON_STDERR, Boolean.toString(tool.isShowConsoleOnStdErr()));
    element.setAttribute(SYNCHRONIZE_AFTER_EXECUTION, Boolean.toString(tool.synchronizeAfterExecution()));
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;


public abstract class ToolsProcessor<T extends Tool> extends NonLazySchemeProcessor<ToolsGroup<T>, ToolsGroup<T>> {
  private static final @NonNls String TOOL_SET = "toolSet";
  private static final @NonNls String TOOL = "tool";
  private static final @NonNls String ATTRIBUTE_NAME = "name";
  private static final @NonNls String NAME = ATTRIBUTE_NAME;
  private static final @NonNls String DESCRIPTION = "description";
  private static final @NonNls String SHOW_IN_MAIN_MENU = "showInMainMenu";
  private static final @NonNls String SHOW_IN_EDITOR = "showInEditor";
  private static final @NonNls String SHOW_IN_PROJECT = "showInProject";
  private static final @NonNls String SHOW_IN_SEARCH_POPUP = "showInSearchPopup";
  private static final @NonNls String DISABLED = "disabled";
  private static final @NonNls String USE_CONSOLE = "useConsole";
  private static final @NonNls String SHOW_CONSOLE_ON_STDOUT = "showConsoleOnStdOut";
  private static final @NonNls String SHOW_CONSOLE_ON_STDERR = "showConsoleOnStdErr";
  private static final @NonNls String SYNCHRONIZE_AFTER_EXECUTION = "synchronizeAfterRun";
  private static final @NonNls String EXEC = "exec";
  private static final @NonNls String WORKING_DIRECTORY = "WORKING_DIRECTORY";
  private static final @NonNls String COMMAND = "COMMAND";
  private static final @NonNls String PARAMETERS = "PARAMETERS";
  private static final @NonNls String FILTER = "filter";
  private static final @NonNls String ELEMENT_OPTION = "option";
  private static final @NonNls String ATTRIBUTE_VALUE = "value";

  @Override
  public @NotNull ToolsGroup<T> readScheme(@NotNull Element root, boolean duringLoad) throws InvalidDataException, IOException, JDOMException {
    if (!TOOL_SET.equals(root.getName())) {
      throw new InvalidDataException();
    }

    String attrName = root.getAttributeValue(ATTRIBUTE_NAME);
    String groupName = StringUtil.isEmpty(attrName)? getDefaultGroupName() : attrName;
    ToolsGroup<T> result = createToolsGroup(groupName);

    final PathMacroManager macroManager = PathMacroManager.getInstance(ApplicationManager.getApplication());

    for (Element element : root.getChildren(TOOL)) {
      T tool = createTool();

      readToolAttributes(element, tool);

      Element exec = element.getChild(EXEC);
      if (exec != null) {
        for (final Element optionElement : exec.getChildren(ELEMENT_OPTION)) {
          String name = optionElement.getAttributeValue(ATTRIBUTE_NAME);
          String value = optionElement.getAttributeValue(ATTRIBUTE_VALUE);

          if (WORKING_DIRECTORY.equals(name)) {
            if (value != null) {
              final String replace = macroManager.expandPath(value).replace('/', File.separatorChar);
              tool.setWorkingDirectory(replace);
            }
          }
          if (COMMAND.equals(name)) {
            tool.setProgram(macroManager.expandPath(BaseToolManager.convertString(value)));
          }
          if (PARAMETERS.equals(name)) {
            tool.setParameters(macroManager.expandPath(BaseToolManager.convertString(value)));
          }
        }
      }

      for (final Element childNode : element.getChildren(FILTER)) {
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
    tool.setName(BaseToolManager.convertString(element.getAttributeValue(NAME)));
    tool.setDescription(BaseToolManager.convertString(element.getAttributeValue(DESCRIPTION)));
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

  protected String getDefaultGroupName() {
    return Tool.DEFAULT_GROUP_NAME;
  }

  protected abstract ToolsGroup<T> createToolsGroup(String groupName);

  protected abstract T createTool();

  @Override
  public @NotNull Element writeScheme(final @NotNull ToolsGroup<T> scheme) throws WriteExternalException {
    Element groupElement = new Element(TOOL_SET);
    groupElement.setAttribute(ATTRIBUTE_NAME, scheme.getName());

    for (T tool : scheme.getElements()) {
      saveTool(tool, groupElement);
    }

    return groupElement;
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

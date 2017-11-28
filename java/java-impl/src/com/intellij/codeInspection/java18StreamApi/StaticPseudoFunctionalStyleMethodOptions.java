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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodOptions {
  private static final String PIPELINE_ELEMENT_NAME = "pipelineElement";
  private static final String FQN_ATTR = "classFqn";
  private static final String METHOD_ATTR = "method";
  private static final String STREAM_API_METHOD_ATTR = "streamApiMethod";
  private static final String LAMBDA_ROLE_ATTR = "lambdaRole";
  private static final String ACCEPTS_DEFAULT_ATTR = "acceptsDefault";
  private static final String DELETE_ATTR = "toDelete";
  private final List<PipelineElement> myElements;

  public StaticPseudoFunctionalStyleMethodOptions() {
    myElements = new ArrayList<>();
    restoreDefault(myElements);
  }

  private static void restoreDefault(final List<PipelineElement> elements) {
    elements.clear();
    String guavaIterables = "com.google.common.collect.Iterables";
    elements.add(new PipelineElement(guavaIterables, "transform", PseudoLambdaReplaceTemplate.MAP));
    elements.add(new PipelineElement(guavaIterables, "filter", PseudoLambdaReplaceTemplate.FILTER));
    elements.add(new PipelineElement(guavaIterables, "find", PseudoLambdaReplaceTemplate.FIND));
    elements.add(new PipelineElement(guavaIterables, "all", PseudoLambdaReplaceTemplate.ALL_MATCH));
    elements.add(new PipelineElement(guavaIterables, "any", PseudoLambdaReplaceTemplate.ANY_MATCH));

    String guavaLists = "com.google.common.collect.Lists";
    elements.add(new PipelineElement(guavaLists, "transform", PseudoLambdaReplaceTemplate.MAP));
  }

  @NotNull
  public Collection<PipelineElement> findElementsByMethodName(final @NotNull String methodName) {
    return ContainerUtil.filter(myElements, element -> methodName.equals(element.getMethodName()));
  }

  public void readExternal(final @NotNull Element xmlElement) {
    restoreDefault(myElements);
    for (Element element : xmlElement.getChildren(PIPELINE_ELEMENT_NAME)) {
      final String fqn = element.getAttributeValue(FQN_ATTR);
      final String method = element.getAttributeValue(METHOD_ATTR);
      final String streamApiMethod = element.getAttributeValue(STREAM_API_METHOD_ATTR);
      final PseudoLambdaReplaceTemplate.LambdaRole lambdaRole =
        PseudoLambdaReplaceTemplate.LambdaRole.valueOf(element.getAttributeValue(LAMBDA_ROLE_ATTR));
      final boolean acceptsDefault = Boolean.valueOf(element.getAttributeValue(ACCEPTS_DEFAULT_ATTR));
      final boolean toDelete = element.getAttribute(DELETE_ATTR) != null;
      final PipelineElement pipelineElement = new PipelineElement(fqn, method, new PseudoLambdaReplaceTemplate(streamApiMethod, lambdaRole, acceptsDefault));
      if (toDelete) {
        myElements.remove(pipelineElement);
      }
      else {
        myElements.add(pipelineElement);
      }
    }
  }

  public void writeExternal(final @NotNull Element xmlElement) {
    final List<PipelineElement> toRemoveElements = new ArrayList<>();
    restoreDefault(toRemoveElements);
    toRemoveElements.removeAll(myElements);

    for (PipelineElement element : toRemoveElements) {
      xmlElement.addContent(createXmlElement(element)
                              .setAttribute(DELETE_ATTR, ""));
    }
    final List<PipelineElement> defaultElements = new ArrayList<>();
    restoreDefault(defaultElements);
    for (PipelineElement element : myElements) {
      if (!defaultElements.contains(element)) {
        xmlElement.addContent(createXmlElement(element));
      }
    }
  }

  public Element createXmlElement(PipelineElement element) {
    final PseudoLambdaReplaceTemplate template = element.getTemplate();
    return new Element(PIPELINE_ELEMENT_NAME)
      .setAttribute(FQN_ATTR, element.getHandlerClass())
      .setAttribute(METHOD_ATTR, element.getMethodName())
      .setAttribute(STREAM_API_METHOD_ATTR, template.getStreamApiMethodName())
      .setAttribute(LAMBDA_ROLE_ATTR, template.getLambdaRole().toString())
      .setAttribute(ACCEPTS_DEFAULT_ATTR, String.valueOf(template.isAcceptDefaultValue()));
  }

  public JComponent createPanel() {
    final JBList list = new JBList(myElements);
    list.setCellRenderer(new ColoredListCellRenderer<PipelineElement>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, PipelineElement element, int index, boolean selected, boolean hasFocus) {
        final String classFQName = element.getHandlerClass();
        final String[] split = classFQName.split("\\.");
        final int classShortNameIndex = classFQName.length() - split[split.length - 1].length();
        append(classFQName.substring(0, classShortNameIndex));
        append(classFQName.substring(classShortNameIndex),
               SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.derive(SimpleTextAttributes.STYLE_BOLD,
                                                                   JBColor.BLUE, null, null));
        append("." + element.getMethodName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    });
    return ToolbarDecorator.createDecorator(list).disableUpDownActions().setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final Project currentProject = CommonDataKeys.PROJECT.getData(button.getDataContext());
        if (currentProject == null) {
          return;
        }
        if (DumbService.isDumb(currentProject)) {
          return;
        }
        final AddMethodsDialog dlg = new AddMethodsDialog(currentProject, list, false);
        if (dlg.showAndGet()) {
          final PipelineElement newElement = dlg.getSelectedElement();
          if (myElements.contains(newElement)) {
            return;
          }
          myElements.add(newElement);
          ((DefaultListModel)list.getModel()).addElement(newElement);
        }
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final int[] indices = list.getSelectedIndices();
        final List<PipelineElement> toRemove = new ArrayList<>(indices.length);
        for (int idx : indices) {
          toRemove.add(myElements.get(idx));
        }
        myElements.removeAll(toRemove);
        ListUtil.removeSelectedItems(list);
      }
    }).createPanel();
  }

  public static class PipelineElement {
    private final String myHandlerClass;
    private final String myMethodName;
    private final PseudoLambdaReplaceTemplate myTemplate;

    public PipelineElement(@NotNull String handlerClass,
                           @NotNull String methodName,
                           @NotNull PseudoLambdaReplaceTemplate template) {
      myHandlerClass = handlerClass;
      myMethodName = methodName;
      myTemplate = template;
    }

    public String getHandlerClass() {
      return myHandlerClass;
    }

    public String getMethodName() {
      return myMethodName;
    }

    public PseudoLambdaReplaceTemplate getTemplate() {
      return myTemplate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PipelineElement element = (PipelineElement)o;

      if (!myHandlerClass.equals(element.myHandlerClass)) return false;
      if (!myMethodName.equals(element.myMethodName)) return false;
      if (!myTemplate.equals(element.myTemplate)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myHandlerClass.hashCode();
      result = 31 * result + myMethodName.hashCode();
      result = 31 * result + myTemplate.hashCode();
      return result;
    }
  }
}

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
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodOptions {
  private static final String PIPELINE_ELEMENT_NAME = "pipelineElement";
  private static final String FQN_ATTR = "classFqn";
  private static final String METHOD_ATTR = "method";
  private static final String STREAM_API_METHOD_ATTR = "streamApiMethod";
  private static final String DELETE_ATTR = "toDelete";
  private final List<PipelineElement> myElements;

  public StaticPseudoFunctionalStyleMethodOptions() {
    myElements = new ArrayList<PipelineElement>();
    restoreDefault(myElements);
  }

  private static void restoreDefault(final List<PipelineElement> elements) {
    elements.clear();
    final String guavaIterables = "com.google.common.collect.Iterables";
    elements.add(new PipelineElement(guavaIterables, "transform", StreamApiConstants.MAP));
    elements.add(new PipelineElement(guavaIterables, "filter", StreamApiConstants.FILTER));
    elements.add(new PipelineElement(guavaIterables, "find", StreamApiConstants.FAKE_FIND_MATCHED));
    elements.add(new PipelineElement(guavaIterables, "all", StreamApiConstants.ALL_MATCH));
    elements.add(new PipelineElement(guavaIterables, "any", StreamApiConstants.ANY_MATCH));
  }

  @NotNull
  public Collection<PipelineElement> findElementsByMethodName(final @NotNull String methodName) {
    return ContainerUtil.filter(myElements, new Condition<PipelineElement>() {
      @Override
      public boolean value(PipelineElement element) {
        return methodName.equals(element.getMethodName());
      }
    });
  }

  public void readExternal(final @NotNull Element xmlElement) {
    restoreDefault(myElements);
    for (Element element : xmlElement.getChildren(PIPELINE_ELEMENT_NAME)) {
      final String fqn = element.getAttributeValue(FQN_ATTR);
      final String method = element.getAttributeValue(METHOD_ATTR);
      final String streamApiMethod = element.getAttributeValue(STREAM_API_METHOD_ATTR);
      final boolean toDelete = element.getAttribute(DELETE_ATTR) != null;
      final PipelineElement pipelineElement = new PipelineElement(fqn, method, streamApiMethod);
      if (toDelete) {
        myElements.remove(pipelineElement);
      }
      else {
        myElements.add(pipelineElement);
      }
    }
  }

  public void writeExternal(final @NotNull Element xmlElement) {
    final List<PipelineElement> toRemoveElements = new ArrayList<PipelineElement>();
    restoreDefault(toRemoveElements);
    toRemoveElements.removeAll(myElements);

    for (PipelineElement element : toRemoveElements) {
      xmlElement.addContent(createXmlElement(element)
                              .setAttribute(DELETE_ATTR, ""));
    }
    final List<PipelineElement> defaultElements = new ArrayList<PipelineElement>();
    restoreDefault(defaultElements);
    for (PipelineElement element : myElements) {
      if (!defaultElements.contains(element)) {
        xmlElement.addContent(createXmlElement(element));
      }
    }
  }

  public Element createXmlElement(PipelineElement element) {
    return new Element(PIPELINE_ELEMENT_NAME)
      .setAttribute(FQN_ATTR, element.getHandlerClass())
      .setAttribute(METHOD_ATTR, element.getMethodName())
      .setAttribute(STREAM_API_METHOD_ATTR, element.getStreamApiMethodName());
  }

  public JComponent createPanel() {
    final JBList list = new JBList();
    list.setModel(new SettingsListModel());

    list.setCellRenderer(new ColoredListCellRenderer<PipelineElement>() {
      @Override
      protected void customizeCellRenderer(JList list, PipelineElement element, int index, boolean selected, boolean hasFocus) {
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
        final AddMethodsDialog dlg = new AddMethodsDialog(currentProject, list, false);
        if (dlg.showAndGet()) {
          final PipelineElement newElement = dlg.getSelectedElement();
          if (myElements.contains(newElement)) {
            return;
          }
          myElements.add(newElement);
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              list.updateUI();
            }
          });
        }
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        myElements.remove(list.getSelectedIndex());
      }
    }).createPanel();
  }

    public static class PipelineElement {
    private final String myHandlerClass;
    private final String myMethodName;
    private final String myStreamApiMethod;

    public PipelineElement(@NotNull String handlerClass, @NotNull String methodName, @NotNull String streamApiMethod) {
      myHandlerClass = handlerClass;
      myMethodName = methodName;
      myStreamApiMethod = streamApiMethod;
    }

    public String getHandlerClass() {
      return myHandlerClass;
    }

    public String getMethodName() {
      return myMethodName;
    }

    public String getStreamApiMethodName() {
      return myStreamApiMethod;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PipelineElement element = (PipelineElement)o;

      if (!myHandlerClass.equals(element.myHandlerClass)) return false;
      if (!myMethodName.equals(element.myMethodName)) return false;
      if (!myStreamApiMethod.equals(element.myStreamApiMethod)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myHandlerClass.hashCode();
      result = 31 * result + myMethodName.hashCode();
      result = 31 * result + myStreamApiMethod.hashCode();
      return result;
    }
  }

  private class SettingsListModel implements ListModel {
    @Override
    public int getSize() {
      return myElements.size();
    }

    @Override
    public PipelineElement getElementAt(int index) {
      return myElements.get(index);
    }

    @Override
    public void addListDataListener(ListDataListener l) {

    }

    @Override
    public void removeListDataListener(ListDataListener l) {

    }
  }
}

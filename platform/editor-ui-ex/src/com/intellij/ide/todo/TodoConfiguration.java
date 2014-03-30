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

package com.intellij.ide.todo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
public class TodoConfiguration implements NamedComponent, JDOMExternalizable {
  private TodoPattern[] myTodoPatterns;
  private TodoFilter[] myTodoFilters;
  private IndexPattern[] myIndexPatterns;

  private final EventDispatcher<PropertyChangeListener> myPropertyChangeMulticaster = EventDispatcher.create(PropertyChangeListener.class);

  @NonNls public static final String PROP_TODO_PATTERNS = "todoPatterns";
  @NonNls public static final String PROP_TODO_FILTERS = "todoFilters";
  @NonNls private static final String ELEMENT_PATTERN = "pattern";
  @NonNls private static final String ELEMENT_FILTER = "filter";
  private final MessageBus myMessageBus;

  /**
   * public for upsource
   */
  public TodoConfiguration(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    resetToDefaultTodoPatterns();
  }

  public void resetToDefaultTodoPatterns() {
    myTodoPatterns = new TodoPattern[]{
      new TodoPattern("\\btodo\\b.*", TodoAttributesUtil.createDefault(), false),
      new TodoPattern("\\bfixme\\b.*", TodoAttributesUtil.createDefault(), false),
    };
    myTodoFilters = new TodoFilter[]{};
    buildIndexPatterns();
  }

  private void buildIndexPatterns() {
    myIndexPatterns = new IndexPattern[myTodoPatterns.length];
    for(int i=0; i<myTodoPatterns.length; i++) {
      myIndexPatterns [i] = myTodoPatterns [i].getIndexPattern();
    }
  }

  public static TodoConfiguration getInstance() {
    return ServiceManager.getService(TodoConfiguration.class);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "TodoConfiguration";
  }

  @NotNull
  public TodoPattern[] getTodoPatterns() {
    return myTodoPatterns;
  }

  @NotNull
  public IndexPattern[] getIndexPatterns() {
    return myIndexPatterns;
  }

  public void setTodoPatterns(@NotNull TodoPattern[] todoPatterns) {
    doSetTodoPatterns(todoPatterns, true);
  }

  private void doSetTodoPatterns(@NotNull TodoPattern[] todoPatterns, final boolean shouldNotifyIndices) {
    TodoPattern[] oldTodoPatterns = myTodoPatterns;
    IndexPattern[] oldIndexPatterns = myIndexPatterns;

    myTodoPatterns = todoPatterns;
    buildIndexPatterns();

    // only trigger index refresh actual index patterns have changed
    if (shouldNotifyIndices && !Arrays.deepEquals(myIndexPatterns, oldIndexPatterns)) {
      final PropertyChangeEvent event =
        new PropertyChangeEvent(this, IndexPatternProvider.PROP_INDEX_PATTERNS, oldTodoPatterns, todoPatterns);
      myMessageBus.syncPublisher(IndexPatternProvider.INDEX_PATTERNS_CHANGED).propertyChange(event);
    }

    // only trigger gui and code daemon refresh when either the index patterns or presentation attributes have changed
    if (!Arrays.deepEquals(myTodoPatterns, oldTodoPatterns)) {
      final PropertyChangeListener multicaster = myPropertyChangeMulticaster.getMulticaster();
      multicaster.propertyChange(new PropertyChangeEvent(this, PROP_TODO_PATTERNS, oldTodoPatterns, todoPatterns));
    }
  }

  /**
   * @return <code>TodoFilter</code> with specified <code>name</code>. Method returns
   *         <code>null</code> if there is no filter with <code>name</code>.
   */
  public TodoFilter getTodoFilter(String name) {
    for (TodoFilter filter : myTodoFilters) {
      if (filter.getName().equals(name)) {
        return filter;
      }
    }
    return null;
  }

  /**
   * @return all <code>TodoFilter</code>s.
   */
  @NotNull
  public TodoFilter[] getTodoFilters() {
    return myTodoFilters;
  }

  public void setTodoFilters(@NotNull TodoFilter[] filters) {
    TodoFilter[] oldFilters = myTodoFilters;
    myTodoFilters = filters;
    myPropertyChangeMulticaster.getMulticaster().propertyChange(new PropertyChangeEvent(this, PROP_TODO_FILTERS, oldFilters, filters));
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeMulticaster.addListener(listener);
  }
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    myPropertyChangeMulticaster.addListener(listener,parentDisposable);
  }
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeMulticaster.removeListener(listener);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    List<TodoPattern> patternsList = new ArrayList<TodoPattern>();
    List<TodoFilter> filtersList = new ArrayList<TodoFilter>();
    for (Element child : element.getChildren()) {
      if (ELEMENT_PATTERN.equals(child.getName())) {
        TodoPattern pattern = new TodoPattern(TodoAttributesUtil.createDefault());
        pattern.readExternal(child, TodoAttributesUtil.getDefaultColorSchemeTextAttributes());
        patternsList.add(pattern);
      }
      else if (ELEMENT_FILTER.equals(child.getName())) {
        TodoPattern[] patterns = patternsList.toArray(new TodoPattern[patternsList.size()]);
        TodoFilter filter = new TodoFilter();
        filter.readExternal(child, patterns);
        filtersList.add(filter);
      }
    }
    doSetTodoPatterns(patternsList.toArray(new TodoPattern[patternsList.size()]), false);
    setTodoFilters(filtersList.toArray(new TodoFilter[filtersList.size()]));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    final TodoPattern[] todoPatterns = myTodoPatterns;
    for (TodoPattern pattern : todoPatterns) {
      Element child = new Element(ELEMENT_PATTERN);
      pattern.writeExternal(child);
      element.addContent(child);
    }
    for (TodoFilter filter : myTodoFilters) {
      Element child = new Element(ELEMENT_FILTER);
      filter.writeExternal(child, todoPatterns);
      element.addContent(child);
    }
  }

  public void colorSettingsChanged() {
    for (TodoPattern pattern : myTodoPatterns) {
      TodoAttributes attributes = pattern.getAttributes();
      if (!attributes.shouldUseCustomTodoColor()) {
        attributes.setUseCustomTodoColor(false, TodoAttributesUtil.getDefaultColorSchemeTextAttributes());
      }
    }
  }
}

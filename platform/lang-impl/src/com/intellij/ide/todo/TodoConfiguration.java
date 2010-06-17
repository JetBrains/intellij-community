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

package com.intellij.ide.todo;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Vladimir Kondratyev
 */
public class TodoConfiguration implements ApplicationComponent, JDOMExternalizable {
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
   * Invoked by reflection
   */
  TodoConfiguration(MessageBus messageBus) {
    myMessageBus = messageBus;
    resetToDefaultTodoPatterns();
  }

  public void resetToDefaultTodoPatterns() {
    myTodoPatterns = new TodoPattern[]{
      new TodoPattern("\\btodo\\b.*", TodoAttributes.createDefault(), false)
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

  @NotNull
  public String getComponentName() {
    return "TodoConfiguration";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public TodoPattern[] getTodoPatterns() {
    return myTodoPatterns;
  }

  @NotNull public IndexPattern[] getIndexPatterns() {
    return myIndexPatterns;
  }

  public void setTodoPatterns(TodoPattern[] todoPatterns) {
    doSetTodoPatterns(todoPatterns, true);
  }

  private void doSetTodoPatterns(TodoPattern[] todoPatterns, final boolean shouldNotifyIndices) {
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
  public TodoFilter[] getTodoFilters() {
    return myTodoFilters;
  }

  public void setTodoFilters(TodoFilter[] filters) {
    TodoFilter[] oldFilters = myTodoFilters;
    myTodoFilters = filters;
    myPropertyChangeMulticaster.getMulticaster().propertyChange(new PropertyChangeEvent(this, PROP_TODO_FILTERS, oldFilters, filters));
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.addListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.removeListener(listener);
  }

  public void readExternal(Element element) throws InvalidDataException {
    ArrayList<TodoPattern> patternsList = new ArrayList<TodoPattern>();
    ArrayList<TodoFilter> filtersList = new ArrayList<TodoFilter>();
    for (Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ELEMENT_PATTERN.equals(child.getName())) {
        TodoPattern pattern = new TodoPattern();
        pattern.readExternal(child);
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

  public void writeExternal(Element element) throws WriteExternalException {
    for (TodoPattern pattern : myTodoPatterns) {
      Element child = new Element(ELEMENT_PATTERN);
      pattern.writeExternal(child);
      element.addContent(child);
    }
    for (TodoFilter filter : myTodoFilters) {
      Element child = new Element(ELEMENT_FILTER);
      filter.writeExternal(child, myTodoPatterns);
      element.addContent(child);
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.search.*;
import com.intellij.util.SmartList;
import com.intellij.util.messages.Topic;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;

@State(name = "TodoConfiguration", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public class TodoConfiguration implements PersistentStateComponent<Element> {
  public static final Topic<PropertyChangeListener> PROPERTY_CHANGE = new Topic<>("TodoConfiguration changes", PropertyChangeListener.class);

  private boolean myMultiLine = true;
  private TodoPattern[] myTodoPatterns;
  private TodoFilter[] myTodoFilters;
  private IndexPattern[] myIndexPatterns;

  @NonNls public static final String PROP_MULTILINE = "multiLine";
  @NonNls public static final String PROP_TODO_PATTERNS = "todoPatterns";
  @NonNls public static final String PROP_TODO_FILTERS = "todoFilters";
  @NonNls private static final String ELEMENT_MULTILINE = "multiLine";
  @NonNls private static final String ELEMENT_PATTERN = "pattern";
  @NonNls private static final String ELEMENT_FILTER = "filter";

  public TodoConfiguration() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        colorSettingsChanged();
      }
    });
    resetToDefaultTodoPatterns();
  }

  public static TodoConfiguration getInstance() {
    return ApplicationManager.getApplication().getService(TodoConfiguration.class);
  }

  public void resetToDefaultTodoPatterns() {
    myTodoPatterns = getDefaultPatterns();
    myTodoFilters = new TodoFilter[]{};
    buildIndexPatterns();
  }

  /**
   * Returns the list of default TO_DO patterns. Can be customized in other IDEs (and is customized in Rider).
   */
  protected TodoPattern @NotNull [] getDefaultPatterns() {
    return new TodoPattern[]{
      new TodoPattern("\\btodo\\b.*", TodoAttributesUtil.createDefault(), false),
      new TodoPattern("\\bfixme\\b.*", TodoAttributesUtil.createDefault(), false),
    };
  }

  private void buildIndexPatterns() {
    myIndexPatterns = new IndexPattern[myTodoPatterns.length];
    for (int i = 0; i < myTodoPatterns.length; i++) {
      myIndexPatterns[i] = myTodoPatterns[i].getIndexPattern();
    }
  }

  public TodoPattern @NotNull [] getTodoPatterns() {
    return myTodoPatterns;
  }

  public IndexPattern @NotNull [] getIndexPatterns() {
    return myIndexPatterns;
  }

  public void setTodoPatterns(TodoPattern @NotNull [] todoPatterns) {
    doSetTodoPatterns(todoPatterns, true);
  }

  private void doSetTodoPatterns(@NotNull TodoPattern @NotNull [] todoPatterns, boolean shouldNotifyIndices) {
    TodoPattern[] oldTodoPatterns = myTodoPatterns;
    IndexPattern[] oldIndexPatterns = myIndexPatterns;

    myTodoPatterns = todoPatterns;
    buildIndexPatterns();

    // only trigger index refresh actual index patterns have changed
    if (shouldNotifyIndices && !Arrays.deepEquals(myIndexPatterns, oldIndexPatterns)) {
      PropertyChangeEvent event = new PropertyChangeEvent(this, IndexPatternProvider.PROP_INDEX_PATTERNS, oldTodoPatterns, todoPatterns);
      getPublisher(IndexPatternProvider.INDEX_PATTERNS_CHANGED).propertyChange(event);
    }

    // only trigger gui and code daemon refresh when either the index patterns or presentation attributes have changed
    if (!Arrays.deepEquals(myTodoPatterns, oldTodoPatterns)) {
      getPublisher(PROPERTY_CHANGE).propertyChange(new PropertyChangeEvent(this, PROP_TODO_PATTERNS, oldTodoPatterns, todoPatterns));
    }
  }

  private static @NotNull PropertyChangeListener getPublisher(@NotNull Topic<PropertyChangeListener> topic) {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(topic);
  }

  /**
   * @return {@code TodoFilter} with specified {@code name}. Method returns
   *         {@code null} if there is no filter with {@code name}.
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
   * @return all {@code TodoFilter}s.
   */
  public TodoFilter @NotNull [] getTodoFilters() {
    return myTodoFilters;
  }

  public boolean isMultiLine() {
    return myMultiLine;
  }

  public void setMultiLine(boolean multiLine) {
    if (multiLine != myMultiLine) {
      myMultiLine = multiLine;
      getPublisher(PROPERTY_CHANGE).propertyChange(new PropertyChangeEvent(this, PROP_MULTILINE, !multiLine, multiLine));
    }
  }

  public void setTodoFilters(TodoFilter @NotNull [] filters) {
    TodoFilter[] oldFilters = myTodoFilters;
    myTodoFilters = filters;
    getPublisher(PROPERTY_CHANGE).propertyChange(new PropertyChangeEvent(this, PROP_TODO_FILTERS, oldFilters, filters));
  }

  @Override
  public void loadState(@NotNull Element element) {
    String multiLineText = element.getChildText(ELEMENT_MULTILINE);
    myMultiLine = multiLineText == null || Boolean.valueOf(multiLineText);

    List<TodoPattern> patternsList = new SmartList<>();
    for (Element child : element.getChildren(ELEMENT_PATTERN)) {
      patternsList.add(new TodoPattern(child, TodoAttributesUtil.getDefaultColorSchemeTextAttributes()));
    }

    TodoPattern[] patterns = patternsList.isEmpty() ? getDefaultPatterns() : patternsList.toArray(new TodoPattern[0]);
    doSetTodoPatterns(patterns, false);

    List<TodoFilter> filtersList = new SmartList<>();
    for (Element child : element.getChildren(ELEMENT_FILTER)) {
      filtersList.add(new TodoFilter(child, Arrays.asList(patterns)));
    }

    if (!(filtersList.isEmpty() && myTodoFilters.length == 0)) {
      setTodoFilters(filtersList.toArray(new TodoFilter[0]));
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    if (!myMultiLine) {
      Element m = new Element(ELEMENT_MULTILINE);
      m.setText(Boolean.FALSE.toString());
      element.addContent(m);
    }
    TodoPattern[] todoPatterns = myTodoPatterns;
    if (!Arrays.equals(myTodoPatterns, getDefaultPatterns())) {
      for (TodoPattern pattern : todoPatterns) {
        Element child = new Element(ELEMENT_PATTERN);
        pattern.writeExternal(child);
        element.addContent(child);
      }
    }

    for (TodoFilter filter : myTodoFilters) {
      Element child = new Element(ELEMENT_FILTER);
      filter.writeExternal(child, todoPatterns);
      element.addContent(child);
    }
    return element;
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

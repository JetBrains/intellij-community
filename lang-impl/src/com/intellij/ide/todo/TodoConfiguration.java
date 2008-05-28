package com.intellij.ide.todo;

import com.intellij.ExtensionPoints;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.EventDispatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class TodoConfiguration implements ApplicationComponent, JDOMExternalizable, IndexPatternProvider {
  private TodoPattern[] myTodoPatterns;
  private TodoFilter[] myTodoFilters;
  private IndexPattern[] myIndexPatterns;

  private EventDispatcher<PropertyChangeListener> myPropertyChangeMulticaster = EventDispatcher.create(PropertyChangeListener.class);

  @NonNls public static final String PROP_TODO_PATTERNS = "todoPatterns";
  @NonNls public static final String PROP_TODO_FILTERS = "todoFilters";
  @NonNls private static final String ELEMENT_PATTERN = "pattern";
  @NonNls private static final String ELEMENT_FILTER = "filter";

  /**
   * Invoked by reflection
   */
  TodoConfiguration() {
    Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INDEX_PATTERN_PROVIDER).registerExtension(this);
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
    return ApplicationManager.getApplication().getComponent(TodoConfiguration.class);
  }

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

  public void setTodoPatterns(TodoPattern[] patterns) {
    TodoPattern[] oldPatterns = myTodoPatterns;
    myTodoPatterns = patterns;
    buildIndexPatterns();
    myPropertyChangeMulticaster.getMulticaster().propertyChange(new PropertyChangeEvent(this, PROP_INDEX_PATTERNS, oldPatterns, patterns));
    myPropertyChangeMulticaster.getMulticaster().propertyChange(new PropertyChangeEvent(this, PROP_TODO_PATTERNS, oldPatterns, patterns));
  }

  /**
   * @return <code>TodoFilter</code> with specified <code>name</code>. Method returns
   *         <code>null</code> if there is no filter with <code>name</code>.
   */
  public TodoFilter getTodoFilter(String name) {
    for (int i = 0; i < myTodoFilters.length; i++) {
      TodoFilter filter = myTodoFilters[i];
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
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element child = (Element)i.next();
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
    setTodoPatterns(patternsList.toArray(new TodoPattern[patternsList.size()]));
    setTodoFilters(filtersList.toArray(new TodoFilter[filtersList.size()]));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (int i = 0; i < myTodoPatterns.length; i++) {
      TodoPattern pattern = myTodoPatterns[i];
      Element child = new Element(ELEMENT_PATTERN);
      pattern.writeExternal(child);
      element.addContent(child);
    }
    for (int i = 0; i < myTodoFilters.length; i++) {
      TodoFilter filter = myTodoFilters[i];
      Element child = new Element(ELEMENT_FILTER);
      filter.writeExternal(child, myTodoPatterns);
      element.addContent(child);
    }
  }
}

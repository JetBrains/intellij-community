// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class TodoFilter implements Cloneable {
  private static final Logger LOG = Logger.getInstance(TodoFilter.class);

  private static final @NonNls String ATTRIBUTE_NAME = "name";
  private static final @NonNls String ELEMENT_PATTERN = "pattern";
  private static final @NonNls String ATTRIBUTE_INDEX = "index";

  private @NlsSafe String myName;
  private Set<TodoPattern> myTodoPatterns;

  public TodoFilter() {
    setName("");
    myTodoPatterns = new HashSet<>();
  }

  public TodoFilter(@NotNull Element element, @NotNull List<TodoPattern> patterns) {
    setName("");
    myTodoPatterns = new HashSet<>();
    readExternal(element, patterns);
  }

  /**
   * @return {@code true} if and only if specified {@code psiFile} has
   * {@code TodoItem}s accepted by the filter.
   */
  public boolean accept(PsiTodoSearchHelper searchHelper, PsiFile psiFile) {
    for (Iterator<TodoPattern> i = iterator(); i.hasNext(); ) {
      TodoPattern todoPattern = i.next();
      if (searchHelper.getTodoItemsCount(psiFile, todoPattern) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return filter's name. That is not {@code null} string.
   */
  @NotNull
  @NlsSafe
  public String getName() {
    return myName;
  }

  public void setName(@NotNull @NlsSafe String name) {
    myName = name;
  }

  /**
   * @return {@code true} if and only if filters contains specified {@code pattern}.
   */
  public boolean contains(TodoPattern pattern) {
    return myTodoPatterns.contains(pattern);
  }

  /**
   * Adds specified {@code pattern} to the set of containing patterns.
   */
  public void addTodoPattern(TodoPattern pattern) {
    LOG.assertTrue(!myTodoPatterns.contains(pattern));
    myTodoPatterns.add(pattern);
  }

  /**
   * Adds specified {@code pattern} from the set of containing patterns.
   */
  public void removeTodoPattern(TodoPattern pattern) {
    LOG.assertTrue(myTodoPatterns.contains(pattern));
    myTodoPatterns.remove(pattern);
  }

  /**
   * @return iterator of containing patterns.
   */
  public Iterator<TodoPattern> iterator() {
    return myTodoPatterns.iterator();
  }

  /**
   * @return {@code true} if and only if filter contains no {@code TodoPattern}s.
   */
  public boolean isEmpty() {
    return myTodoPatterns.isEmpty();
  }

  private void readExternal(@NotNull Element element, @NotNull List<TodoPattern> patterns) {
    myName = element.getAttributeValue(ATTRIBUTE_NAME);
    if (myName == null) {
      throw new IllegalArgumentException();
    }

    myTodoPatterns.clear();
    for (Element child : element.getChildren(ELEMENT_PATTERN)) {
      try {
        int index = Integer.parseInt(child.getAttributeValue(ATTRIBUTE_INDEX));
        if (index < 0 || index > patterns.size() - 1) {
          continue;
        }
        TodoPattern pattern = patterns.get(index);
        if (myTodoPatterns.contains(pattern)) {
          continue;
        }
        myTodoPatterns.add(pattern);
      }
      catch (NumberFormatException ignored) {
      }
    }
  }

  /**
   * @param element  in which all data will be stored
   * @param patterns all available patterns
   */
  public void writeExternal(Element element, TodoPattern[] patterns) {
    element.setAttribute(ATTRIBUTE_NAME, myName);
    for (TodoPattern pattern : myTodoPatterns) {
      int index = ArrayUtilRt.find(patterns, pattern);
      LOG.assertTrue(index != -1);
      Element child = new Element(ELEMENT_PATTERN);
      child.setAttribute(ATTRIBUTE_INDEX, Integer.toString(index));
      element.addContent(child);
    }
  }

  public int hashCode() {
    int hashCode = myName.hashCode();
    for (TodoPattern myTodoPattern : myTodoPatterns) {
      hashCode += myTodoPattern.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof TodoFilter)) {
      return false;
    }
    TodoFilter filter = (TodoFilter)obj;

    if (!myName.equals(filter.myName)) {
      return false;
    }

    if (myTodoPatterns.size() != filter.myTodoPatterns.size()) {
      return false;
    }

    for (TodoPattern pattern : myTodoPatterns) {
      if (!filter.contains(pattern)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public TodoFilter clone() {
    try {
      TodoFilter filter = (TodoFilter)super.clone();
      filter.myTodoPatterns = new HashSet<>(myTodoPatterns);
      return filter;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }
}
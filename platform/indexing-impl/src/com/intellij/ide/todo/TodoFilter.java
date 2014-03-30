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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class TodoFilter implements Cloneable{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.TodoFilter");

  private String myName;
  // TODO[vova] use array for storing TodoPatterns. Perhaps it's better...
  private HashSet<TodoPattern> myTodoPatterns;
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ELEMENT_PATTERN = "pattern";
  @NonNls private static final String ATTRIBUTE_INDEX = "index";

  /**
   * Creates filter with empty name and empty set of patterns.
   */
  public TodoFilter(){
    setName("");
    myTodoPatterns=new HashSet<TodoPattern>(1);
  }

  /**
   * @return <code>true</code> if and only if specified <code>psiFile</code> has
   * <code>TodoItem</code>s accepted by the filter.
   */
  public boolean accept(PsiTodoSearchHelper searchHelper,PsiFile psiFile){
    for(Iterator<TodoPattern> i=iterator();i.hasNext();){
      TodoPattern todoPattern= i.next();
      if(searchHelper.getTodoItemsCount(psiFile,todoPattern)>0){
        return true;
      }
    }
    return false;
  }

  /**
   * @return filter's name. That is not <code>null</code> string.
   */
  public String getName(){
    return myName;
  }

  public void setName(@NotNull String name){
    myName=name;
  }

  /**
   * @return <code>true</code> if and only if filters contains specified <code>pattern</code>.
   */
  public boolean contains(TodoPattern pattern){
    return myTodoPatterns.contains(pattern);
  }

  /**
   * Adds specified <code>pattern</code> to the set of containing patterns.
   */
  public void addTodoPattern(TodoPattern pattern){
    LOG.assertTrue(!myTodoPatterns.contains(pattern));
    myTodoPatterns.add(pattern);
  }

  /**
   * Adds specified <code>pattern</code> from the set of containing patterns.
   */
  public void removeTodoPattern(TodoPattern pattern){
    LOG.assertTrue(myTodoPatterns.contains(pattern));
    myTodoPatterns.remove(pattern);
  }

  /**
   * @return iterator of containing patterns.
   */
  public Iterator<TodoPattern> iterator(){
    return myTodoPatterns.iterator();
  }

  /**
   * @return <code>true</code> if and only if filter contains no <code>TodoPattern</code>s.
   */
  public boolean isEmpty(){
    return myTodoPatterns.isEmpty();
  }

  /**
   * @param element with filter's data.
   * @param patterns all available patterns
   */
  public void readExternal(Element element,TodoPattern[] patterns) {
    myName=element.getAttributeValue(ATTRIBUTE_NAME);
    if(myName==null){
      throw new IllegalArgumentException();
    }
    myTodoPatterns.clear();
    for (Object o : element.getChildren()) {
      Element child = (Element)o;
      if (!ELEMENT_PATTERN.equals(child.getName())) {
        continue;
      }
      try {
        int index = Integer.parseInt(child.getAttributeValue(ATTRIBUTE_INDEX));
        if (index < 0 || index > patterns.length - 1) {
          continue;
        }
        TodoPattern pattern = patterns[index];
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
   * @param element in which all data will be stored
   * @param patterns all available patterns
   */
  public void writeExternal(Element element, TodoPattern[] patterns){
    element.setAttribute(ATTRIBUTE_NAME,myName);
    for (TodoPattern pattern : myTodoPatterns) {
      int index = ArrayUtilRt.find(patterns, pattern);
      LOG.assertTrue(index != -1);
      Element child = new Element(ELEMENT_PATTERN);
      child.setAttribute(ATTRIBUTE_INDEX, Integer.toString(index));
      element.addContent(child);
    }
  }

  public int hashCode(){
    int hashCode=myName.hashCode();
    for (TodoPattern myTodoPattern : myTodoPatterns) {
      hashCode += myTodoPattern.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj){
    if(!(obj instanceof TodoFilter)){
      return false;
    }
    TodoFilter filter=(TodoFilter)obj;

    if(!myName.equals(filter.myName)){
      return false;
    }

    if(myTodoPatterns.size()!=filter.myTodoPatterns.size()){
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
  public TodoFilter clone(){
    try{
      TodoFilter filter = (TodoFilter)super.clone();
      filter.myTodoPatterns=new HashSet<TodoPattern>(myTodoPatterns);
      return filter;
    }catch(CloneNotSupportedException e){
      LOG.error(e);
      return null;
    }
  }
}
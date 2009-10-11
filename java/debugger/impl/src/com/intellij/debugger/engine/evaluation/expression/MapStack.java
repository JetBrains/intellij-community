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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.openapi.diagnostic.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 24, 2004
 * Time: 10:36:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class MapStack<Key, Value> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.MapStack");

  private final LinkedList<HashMap<Key, Value>> myStack;

  public MapStack() {
    myStack = new LinkedList<HashMap<Key,Value>>();
    push();
  }

  public void push(){
    myStack.addFirst(new HashMap<Key, Value>());
  }

  public void pop(){
    myStack.removeFirst();
    LOG.assertTrue(myStack.size() > 0);
  }

  private HashMap<Key, Value> current() {
    return myStack.getFirst();
  }

  public void put(Key key, Value value) {
    current().put(key, value);
  }

  public boolean containsKey(Key key){
    for (Iterator<HashMap<Key, Value>> iterator = myStack.iterator(); iterator.hasNext();) {
      HashMap<Key, Value> hashMap = iterator.next();
      if(hashMap.containsKey(key))return true;
    }
    return false;        
  }

  public Value get(Key key) {
    for (Iterator<HashMap<Key, Value>> iterator = myStack.iterator(); iterator.hasNext();) {
      HashMap<Key, Value> hashMap = iterator.next();
      Value value = hashMap.get(key);
      if(value != null) return value;
    }
    return null;
  }
}

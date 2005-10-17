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

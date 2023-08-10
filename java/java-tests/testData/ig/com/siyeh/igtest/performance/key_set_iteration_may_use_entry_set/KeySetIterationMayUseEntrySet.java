package com.siyeh.igtest.performance.key_set_iteration_may_use_entry_set;

import java.util.*;


public class KeySetIterationMayUseEntrySet {

  void foo(Map<String, String> m) {
    for (String k : <warning descr="Iteration over 'm.keySet()' may be replaced with 'values()' iteration">m.keySet()</warning>) {
      System.out.println(m.get((k)));
    }
  }

  void bar() {
    HashMap<String, String> map = get();
    for (String a : <warning descr="Iteration over 'map.keySet()' may be replaced with 'values()' iteration">map.keySet()</warning>) {
      System.out.println(map.get(a));
    }
  }

  void bar1() {
    HashMap<String, String> map = get();
    for (String a : <warning descr="Iteration over 'map.keySet()' may be replaced with 'values()' iteration">map.keySet()</warning>) {
      String val = map.get(a);
      System.out.println(val);
    }
  }

  void bar2() {
    HashMap<String, String> map = get();
    for (String a : <warning descr="Iteration over 'map.keySet()' may be replaced with 'entrySet()' iteration">map.keySet()</warning>) {
      String val = map.get(a);
      System.out.println(a+"="+val);
    }
  }
  
  void forEach(Map<String, String> map) {
    <warning descr="Iteration over 'map.keySet()' may be replaced with 'Map.forEach()' iteration">map.keySet()</warning>.forEach(x -> {
      String value = map.get(x);
      System.out.println(x+"="+value);
    });
  }
  
  void forEach2(Map<String, String> map) {
    Set<String> keySet = map.keySet();
    (<warning descr="Iteration over 'keySet' may be replaced with 'Map.forEach()' iteration">keySet</warning>).forEach(x -> {
      String value = map.get(x);
      System.out.println(x+"="+value);
    });
  }

  private Object[] shrink(Hashtable<?, ?> tmp) {
    Object[] array = new Object[tmp.size() * 2];
    int j = 0;
    for (Object o : <warning descr="Iteration over 'tmp.keySet()' may be replaced with 'entrySet()' iteration">tmp.keySet()</warning>) {
      array[j] = o;
      array[j + 1] = tmp.get(o);
      j += 2;
    }
    return array;
  }
  
  void inc(Map<Integer, Long> map) {
    for (Integer key : map.keySet()) {
      key++;
      System.out.println(map.get(key));
    }
  }

  HashMap<String, String> get() {
    return null;
  }

  class MyClass {
    private Map<String, Integer> map;

    public MyClass(Map<String, Integer> map) {
      this.map = map;
    }

    public void myMethod(MyClass other) {
      for (String key : map.keySet()) {
        Integer valueFromMap2 = other.map.get(key);
      }
    }
  }
}
class EntryIterationBug {
  private final Map<String, Double> map = new HashMap<>();

  public void merge(EntryIterationBug other) {
    for (String s : other.map.keySet()) {
      System.out.println(map.get(s));
    }
  }
}

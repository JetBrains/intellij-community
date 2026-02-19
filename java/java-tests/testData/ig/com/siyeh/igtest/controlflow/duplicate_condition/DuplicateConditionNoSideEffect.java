package com.siyeh.igtest.controlflow.duplicate_condition;

import java.util.*;

public class DuplicateConditionNoSideEffect {
  public void foo()
  {
    if(bar()||bar())
    {
      System.out.println("1");
    }else if(bar()|| true)
    {
      System.out.println("2");
    }

    if(<warning descr="Duplicate condition 'baz()'">baz()</warning>||<warning descr="Duplicate condition 'baz()'">baz()</warning>)
    {
      System.out.println("1");
    }else if(<warning descr="Duplicate condition 'baz()'">baz()</warning>|| true)
    {
      System.out.println("2");
    }
  }

  int x;

  public void interrupted() {
    if(<warning descr="Duplicate condition 'x == 2'">x == 2</warning>) {
      System.out.println(1);
      return;
    }
    if(x == 3) {
      System.out.println(2);
      return;
    }
    if(<warning descr="Duplicate condition 'x == 2'">x == 2</warning>) {
      System.out.println(3);
      return;
    }
    if(update()) {
      System.out.println("updated");
      return;
    }
    if(x == 2) {
      System.out.println("Possible after update");
      return;
    }
  }

  boolean update() {
    x = 2;
    return true;
  }

  // purity not inferred for virtual methods
  public boolean bar()
  {
    return true;
  }

  // purity inferred
  public static boolean baz()
  {
    return true;
  }

  void testCollection(Set<String> set) {
    if (set.add("foo") || set.remove("bar") || set.add("foo") || set.remove("bar")) {}
  }
}
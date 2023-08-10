/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.igtest.controlflow.duplicate_condition;

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
}
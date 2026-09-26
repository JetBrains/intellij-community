package com.siyeh.igtest.style.unnecessarily_qualified_statically_imported_element;

import static java.lang.Math.PI;
import static java.lang.Math.E;
import static java.lang.Math.abs;
import static java.lang.Math.sin;
import static java.util.Map.Entry;

class C {

  {
    System.out.println(Math.PI);
    Math.abs(10);
    //Map.Entry entry;
    System.out.println(Math.E);
    Math.sin(0.0);
  }

  int E;
  void sin() {}
  
}
package org.example;


import ccc.CCC;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

class IncorrectOrderWithoutModuleImport {
  public static void main(String[] args) {
    List<String> a = new ArrayList<String>();
    a.add("a");
    final List<String> list = asList(args);
    new CCC();
  }
}
package org.example;


import ccc.CCC;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

class IncorrectOrderWithoutModuleImport {
  public static void main(String[] args) {
    List<String> a = new ArrayList<String>();
    a.add("a");
    final List<String> list = asList(args);
    new CCC();
  }
}
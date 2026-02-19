package org.example;


import module java.base;
import ccc.CCC;
import static java.util.Arrays.asList;

class IncorrectOrderWithoutModuleImport {
  public static void main(String[] args) {
    List<String> a = new ArrayList<String>();
    a.add("a");
    final List<String> list = asList(args);
    new CCC();
  }
}
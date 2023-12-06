package com.siyeh.igfixes.jdk.vararg_parameter;

@SuppressWarnings("UnusedDeclaration")
public class GenericType {
  final void addClasses(Class<? extends Number>[] classes) {
  }

  void test(Class<? extends Number>... classes) {
    addClasses(new Class[]{Number.class, Byte.class});
    addClasses(classes);
  }
}
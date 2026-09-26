package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    @<caret>SuppressWarnings("blabla")
    @Deprecated
    Object foo() {
        return null;
    }
}
@interface ValueAnnotation {
  String[] value();
}
@interface ArrayAnnotation {
  String[] array();
}
@ValueAnnotation("the value")
@ArrayAnnotation(array = "first")
class MyClass {

  @ ValueAnnotation
  int foo(@ArrayAnnotation(array="") String s) {
    return -1;
  }

  @Two(i = 1, j = 2)
  String s;
}
@interface Two {
  int[] i();
  int j();
}
/*1*/
@Two(
        i = 10,/*2*/
        j = 11/*3*/
)
class X { }
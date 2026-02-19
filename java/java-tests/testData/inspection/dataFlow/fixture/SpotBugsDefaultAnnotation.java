package edu.umd.cs.findbugs.annotations;

import java.lang.annotation.Annotation;

@interface DefaultAnnotationForParameters {
  Class<? extends Annotation>[] value();
}

@interface NonNull {
}

@DefaultAnnotationForParameters(NonNull.class)
class X {
  native String get();
  
  void test(String s) {
    if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
    if (get() == null) {}
  }
}
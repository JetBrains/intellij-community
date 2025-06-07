package org.jetbrains.jps.builders.java.dependencyView;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface MockAnnotation {

  int param1() default 1;
  int param2() default 2;

  EnumParam enumParam() default EnumParam.VALUE_1;

  enum EnumParam {
    VALUE_1, VALUE_2, VALUE_3;
  }
}


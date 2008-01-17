package com.intellij.idea;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Bombed {
  int year() default 2008;
  int month();
  int day();
  int time() default 0;
  String user() default "unknown, or, rather, Max";
  String description() default "";
}

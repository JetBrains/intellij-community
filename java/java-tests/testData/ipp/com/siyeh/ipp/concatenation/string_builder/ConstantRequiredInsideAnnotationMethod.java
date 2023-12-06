package com.siyeh.ipp.concatenation.string_builder;

public @interface ConstantRequiredInsideAnnotationMethod {
  String val() default "hey," <caret>+ "";
}
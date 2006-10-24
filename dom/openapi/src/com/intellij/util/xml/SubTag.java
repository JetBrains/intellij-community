/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotates DOM attribute children getters, that should return {@link DomElement}.
 * The getters may be annotated with {@link @com.intellij.util.xml.Convert()} annotation, if it returns {@link GenericDomValue} inheritor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SubTag {
  /**
   * @return child XML tag name if it can't be inferred from the getter name (see {@link @com.intellij.util.xml.NameStrategy()})
   */
  String value() default "";

  /**
   * @return if there are several child XML tags with the same name (e.g. always 2), the number of the child tag we should deal with
   */
  int index() default 0;

  /**
   * @return for methods returning {@link com.intellij.util.xml.GenericDomValue}<{@link Boolean}>, defines, whether the Boolean.TRUE should
   * correspond just to empty tag existence, and Boolean.FALSE - to unexistence. {@link #index()} should be always 0 in such a case.
   */
  boolean indicator() default false;
}

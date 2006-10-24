/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotates a collection children getter, which should return generic {@link java.util.Collection} or {@link java.util.List}. 
 *
 * @author peter
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SubTagList {

  /**
   * @return child XML tag name if it can't be inferred from the getter name (see {@link @com.intellij.util.xml.NameStrategy()})
   */
  String value() default "";
}

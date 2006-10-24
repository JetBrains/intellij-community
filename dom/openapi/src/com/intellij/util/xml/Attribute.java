/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotates DOM attribute children getters, that should return {@link com.intellij.util.xml.GenericAttributeValue}.
 * The getters may be annotated with {@link @com.intellij.util.xml.Convert()} annotation.
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Attribute {
  /**
   * @return XML attribute name if it can't be inferred from the getter name (see {@link @com.intellij.util.xml.NameStrategy()})
   */
  String value() default "";
}

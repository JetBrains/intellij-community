/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotates DOM methods that are shoutcuts to long call chains. For example, if you often need
 * to write getFoo().getBar().getZip(), you can instead create a method getFBZ() and annotate it
 * with @PropertyAccessor({"foo", "bar", "zip"}).
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface PropertyAccessor {
  /**
   * @return list of property names corresponding to methods you want to replace calls to
   */
  String[] value();
}

/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * All DOM methods that return something nontrivial, not String, Integer, Boolean, PsiClass, PsiType, or {@link com.intellij.util.xml.GenericValue}
 * parameterized with all these elements, should be annotated with this annotation. The {@link #value()} parameter should
 * specify {@link com.intellij.util.xml.Converter} class able to convert this custom type to and from {@link String}.  
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Convert {
  /**
   * @return Converter class
   */
  Class<? extends Converter> value();

  /**
   * @return whether the corresponding XML reference to be soft. Soft references are not highlighted as errors, if unresolved.
   */
  boolean soft() default false;
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Dmitry Avdeev
 *
 * @see com.intellij.util.xml.CustomReferenceConverter
 * @see @com.intellij.util.xml.Convert()
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Referencing {

  Class<? extends CustomReferenceConverter> value();

  /**
   * @return whether the corresponding XML reference to be soft. Soft references are not highlighted as errors, if unresolved.
   */
  boolean soft() default false;
}

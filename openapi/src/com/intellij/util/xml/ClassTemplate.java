/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.util.ClassKind;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * To be used together with {@link com.intellij.util.xml.ExtendClass}.
 *
 * If specified, a 'create from usage' quick fix will create class based on the {@link #value()} template.
 *
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassTemplate {
  @NonNls String value();

  /**
   * @return affects the quick fix presentable text, 'Create class ...' or 'Create interface ...', etc.
   */
  ClassKind kind() default ClassKind.CLASS;

}

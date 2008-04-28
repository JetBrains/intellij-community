/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import java.lang.annotation.Annotation;

/**
 * @author peter
*/
@SuppressWarnings({"ClassExplicitlyAnnotation"})
public abstract class ExtendClassImpl implements ExtendClass {

  public boolean instantiatable() {
    return false;
  }

  public boolean canBeDecorator() {
    return false;
  }

  public boolean allowEmpty() {
    return false;
  }

  public boolean allowAbstract() {
    return true;
  }

  public boolean allowInterface() {
    return true;
  }

  public Class<? extends Annotation> annotationType() {
    return ExtendClass.class;
  }
}

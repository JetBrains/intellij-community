/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

/**
 * The base interface class for all components.
 *
 * @see ApplicationComponent
 * @see ProjectComponent
 */
public interface BaseComponent {
  /**
   * Unique name of this component. If there is another component with the same name or
   * name is null internal assertion will occur.
   *
   * @return the name of this component
   */
  String getComponentName();

  /**
   *  Component should do initialization and communication with another components in this method.
   */
  void initComponent();

  /**
   *  Component should dispose system resources or perform another cleanup in this method.
   */
  void disposeComponent();
}

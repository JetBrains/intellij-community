/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * Register DOM extenders via dom.extender extension point. Specify 2 attributes:
 *   domClass - the DOM element class for which this extender will be called. Should be equal to T.
 *   extenderClass - this class qualified name.
 *
 * @author peter
 */
public abstract class DomExtender<T extends DomElement> {

  /**
   * @param t DOM element where new children may be added to
   * @param registrar a place to register your own DOM children descriptions
   * @return dependency items, whose change should trigger dynamic DOM rebuild for this element   
   */
  public abstract Object[] registerExtensions(@NotNull T t, @NotNull final DomExtensionsRegistrar registrar);
}

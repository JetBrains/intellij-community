// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.presentation;

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers icon and type name for element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Presentation {

  /**
   * @return Path to image resource ({@code /foo/bar/MyIcon.png}) or classname (w/o "icons" package)/FQN with icon field ({@code MyIcons.CustomIcon}).
   * @see <a href="https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html">Working with Icons and Images</a>
   */
  String icon() default "";

  Class<? extends PresentationProvider> provider() default PresentationProvider.class;

  /**
   * Non-localized type name. Use {@link TypeNameEP} or provide localized text via {@link PresentationProvider#getTypeName(Object)} for i18n.
   */
  @NonNls
  String typeName() default "";
}

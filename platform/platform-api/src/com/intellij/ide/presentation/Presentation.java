// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.presentation;

import com.intellij.ide.TypeNameEP;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Dmitry Avdeev
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Presentation {

  /**
   * @return Path to image resource ({@code /foo/bar/MyIcon.png}) or FQN (w/o "icons" package) to icon field ({@code MyIcons.CustomIcon}).
   */
  String icon() default "";

  Class<? extends PresentationProvider> provider() default PresentationProvider.class;

  /**
   * Non-localized type name. Use {@link TypeNameEP} or provide localized text via {@link PresentationProvider#getTypeName(Object)} for i18n.
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  String typeName() default "";
}

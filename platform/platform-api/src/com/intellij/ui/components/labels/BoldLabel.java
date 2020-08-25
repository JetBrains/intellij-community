// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author kir
 */
@SuppressWarnings({"ClassWithTooManyConstructors"})
public class BoldLabel extends JLabel {

  public BoldLabel() {
  }

  public BoldLabel(@NlsContexts.Label String text) {
    super(toHtml(text));
  }

  public BoldLabel(@NlsContexts.Label String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(toHtml(text), horizontalAlignment);
  }

  public BoldLabel(Icon image) {
    super(image);
  }

  public BoldLabel(Icon image, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public BoldLabel(@NlsContexts.Label String text, Icon icon, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(toHtml(text), icon, horizontalAlignment);
  }

  @Override
  public void setText(@NlsContexts.Label String text) {
    super.setText(toHtml(text));
  }

  private static @Nls String toHtml(@Nls String text) {
    if (text.startsWith("<html>")) return text;
    return XmlStringUtil.wrapInHtml(
      "<b>" + text.replaceAll("\\n", "<br>") + "</b>" //NON-NLS
    );
  }
}

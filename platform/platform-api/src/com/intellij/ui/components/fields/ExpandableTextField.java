// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Expandable;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * @author Sergey Malenkov
 */
public class ExpandableTextField extends ExtendableTextField implements Expandable {
  private final ExpandableSupport support;

  /**
   * Creates an expandable text field with the default line parser/joiner,
   * that uses a whitespaces to split a string to several lines.
   */
  public ExpandableTextField() {
    this(ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  /**
   * Creates an expandable text field with the specified line parser/joiner.
   *
   * @see ParametersListUtil
   */
  public ExpandableTextField(@NotNull Function<String, List<String>> parser, @NotNull Function<List<String>, String> joiner) {
    Function<String, String> onShow = text -> StringUtil.join(parser.fun(text), "\n");
    Function<String, String> onHide = text -> joiner.fun(asList(StringUtil.splitByLines(text)));
    support = new ExpandableSupport.TextComponent<>(this, onShow, onHide);
    putClientProperty("monospaced", true);
    setExtensions(createExtensions());
  }

  protected List<ExtendableTextComponent.Extension> createExtensions() {
    return singletonList(ExpandableSupport.createExpandExtension(support));
  }

  public String getTitle() {
    return support.getTitle();
  }

  public void setTitle(String title) {
    support.setTitle(title);
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (!enabled) collapse();
    super.setEnabled(enabled);
  }

  @Override
  public void collapse() {
    support.collapse();
  }

  @Override
  public boolean isExpanded() {
    return support.isExpanded();
  }

  @Override
  public void expand() {
    support.expand();
  }
}

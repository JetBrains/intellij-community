// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;

/**
 * @author Bas Leijdekkers
 */
public class RegExpInspectionConfigurationCellRenderer extends ColoredListCellRenderer<RegExpInspectionConfiguration.InspectionPattern> {

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends RegExpInspectionConfiguration.InspectionPattern> list,
                                       RegExpInspectionConfiguration.InspectionPattern value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
    final FileType fileType = value.fileType();
    setIcon((fileType == null) ? AllIcons.FileTypes.Any_type : fileType.getIcon());
    final String regExp = value.regExp();
    final String replacement = value.replacement();
    append("/", SimpleTextAttributes.GRAY_ATTRIBUTES);
    if (replacement != null) {
      append(shortenTextWithEllipsis(regExp, 49, 0, true), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append("/ ⇨ '", SimpleTextAttributes.GRAY_ATTRIBUTES);
      append(shortenTextWithEllipsis(replacement, 49, 0, true), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append("'", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      append(shortenTextWithEllipsis(regExp, 100, 0, true), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append("/", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    setEnabled(list.isEnabled());
  }
}

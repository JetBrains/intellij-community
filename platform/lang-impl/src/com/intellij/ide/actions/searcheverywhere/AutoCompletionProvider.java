// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class AutoCompletionProvider {

  public static List<SearchEverywhereFoundElementInfo> getCompletionElements(Collection<SearchEverywhereContributor<?>> contributors,
                                                                             JTextComponent textComponent) {
    StubContributor stubContributor = new StubContributor(textComponent);
    return ContainerUtil.map(
      getCompletions(contributors, textComponent),
      command -> new SearchEverywhereFoundElementInfo(command, 0, stubContributor));
  }

  private static List<AutoCompletionCommand> getCompletions(Collection<SearchEverywhereContributor<?>> contributors,
                                                            JTextComponent textComponent) {

    return contributors.stream()
      .filter(c -> c instanceof AutoCompletionContributor)
      .map(c -> ((AutoCompletionContributor)c))
      .flatMap(c -> c.getAutocompleteItems(textComponent.getText(), textComponent.getCaretPosition()).stream())
      .collect(Collectors.toList());
  }

  private static final class StubContributor implements SearchEverywhereContributor<AutoCompletionCommand> {

    private final JTextComponent myTextComponent;

    private StubContributor(JTextComponent component) { myTextComponent = component; }

    @Override
    public @NotNull String getSearchProviderId() {
      return "AutocompletionContributor";
    }

    @Override
    public @Nls @NotNull String getGroupName() {
      return IdeBundle.message("searcheverywhere.autocompletion.tab.name");
    }

    @Override
    public int getSortWeight() {
      return 0;
    }

    @Override
    public boolean showInFindResults() {
      return false;
    }

    @Override
    public void fetchElements(@NotNull String pattern,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull Processor<? super AutoCompletionCommand> consumer) { }

    @Override
    public boolean processSelectedItem(@NotNull AutoCompletionCommand selected,
                                       int modifiers,
                                       @NotNull String searchText) {
      selected.completeQuery(myTextComponent);
      return false;
    }

    @Override
    public @NotNull ListCellRenderer<? super AutoCompletionCommand> getElementsRenderer() {
      return new CommandRenderer();
    }
  }

  private static final class CommandRenderer extends SimpleColoredComponent implements ListCellRenderer<AutoCompletionCommand> {

    private boolean mySelected;
    private Color myForeground;
    private Color mySelectionForeground;

    @Override
    public Component getListCellRendererComponent(JList<? extends AutoCompletionCommand> list,
                                                  AutoCompletionCommand value, int index, boolean isSelected, boolean cellHasFocus) {

      clear();
      getIpad().left = getIpad().right = UIUtil.isUnderWin10LookAndFeel() ? 0 : JBUIScale.scale(UIUtil.getListCellHPadding());

      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setFont(list.getFont());
      setIcon(EmptyIcon.ICON_16);

      mySelected = isSelected;
      myForeground = isEnabled() ? list.getForeground() : UIUtil.getLabelDisabledForeground();
      mySelectionForeground = list.getSelectionForeground();

      if (UIUtil.isUnderWin10LookAndFeel()) {
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      }
      else {
        setBackground(isSelected ? list.getSelectionBackground() : null);
      }

      SimpleTextAttributes baseStyle = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground());
      appendHTML(value.getPresentationString(), baseStyle);

      return this;
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
      if (mySelected) {
        super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), mySelectionForeground), isMainText);
      }
      else if (attributes.getFgColor() == null) {
        super.append(fragment, attributes.derive(-1, myForeground, null, null), isMainText);
      }
      else {
        super.append(fragment, attributes, isMainText);
      }
    }
  }
}

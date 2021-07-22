// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class AutoCompletionProvider {

  public static List<SearchEverywhereFoundElementInfo> getCompletionElements(Collection<SearchEverywhereContributor<?>> contributors, JTextComponent textComponent) {
    StubContributor stubContributor = new StubContributor(textComponent);
    return ContainerUtil.map(
      getCompletions(contributors, textComponent),
      command -> new SearchEverywhereFoundElementInfo(command, 0, stubContributor));
  }

  private static List<AutoCompletionCommand> getCompletions(Collection<SearchEverywhereContributor<?>> contributors, JTextComponent textComponent) {
    return contributors.stream()
      .filter(c -> c instanceof AutoCompletionContributor)
      .map(c -> ((AutoCompletionContributor)c))
      .flatMap(c -> c.getAutocompleteItems(textComponent.getText(), textComponent.getCaretPosition()).stream())
      .collect(Collectors.toList());
  }

  private static class StubContributor implements SearchEverywhereContributor<AutoCompletionCommand> {

    private final JTextComponent myTextComponent;

    private StubContributor(JTextComponent component) { myTextComponent = component; }

    @Override
    public @NotNull String getSearchProviderId() {
      return "AutocompletionContributor";
    }

    @Nls
    @Override
    public @NotNull String getGroupName() {
      return "Autocompletion";
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
                              @NotNull Processor<? super AutoCompletionCommand> consumer) {}

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

    @Override
    public @Nullable Object getDataForItem(@NotNull AutoCompletionCommand element,
                                           @NotNull String dataId) {
      return null;
    }
  }

  private static class CommandRenderer implements ListCellRenderer<AutoCompletionCommand> {
    @Override
    public Component getListCellRendererComponent(JList<? extends AutoCompletionCommand> list,
                                                  AutoCompletionCommand value, int index, boolean isSelected, boolean cellHasFocus) {
      return new JLabel(value.getPresentationString());
    }
  }

}

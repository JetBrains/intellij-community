// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contributor which provides and processes items for <i>Search Everywhere</i> dialog.
 *
 * @author Konstantin Bulenkov
 * @author Mikhail Sokolov
 */
public interface SearchEverywhereContributor<Item> extends PossiblyDumbAware, Disposable {

  ExtensionPointName<SearchEverywhereContributorFactory<?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");

  /**
   * Unique ID of provider. Usually {@link Class#getSimpleName()} of the implementing class is used.
   */
  @NotNull
  String getSearchProviderId();

  /**
   * Display name for the group defined by this contributor. This name is shown in:
   * <ul>
   *   <li>Contributor tab if there is separate tab for this contributor</li>
   *   <li>Group separator in results list when splitting by groups is enabled in results</li>
   * </ui>
   */
  @NotNull
  @Nls
  String getGroupName();

  /**
   * Full group name for contributor. Used in filters, empty results placeholders, etc.
   * Usually equals to {@code getGroupName}.
   */
  default @NotNull @Nls String getFullGroupName() {
    return getGroupName();
  }

  /**
   * <p>Defines weight for sorting contributors (<b>not elements</b>).
   * This weight is used for example for ordering groups in results list when splitting by groups is enabled.</p>
   *
   * <p>Please do not use this method to set found items weights. For this purposes look at {@link SearchEverywhereContributor#getElementPriority(Object, String)}
   * and {@link WeightedSearchEverywhereContributor#fetchWeightedElements(String, ProgressIndicator, Processor)} methods.</p>
   */
  int getSortWeight();

  /**
   * Defines if results found by this contributor can be shown in <i>Find</i> toolwindow.
   */
  boolean showInFindResults();

  /**
   * <p>Defines if separate tab should be shown for this contributor in <i>Search Everywhere</i> dialog.</p>
   * <p>Please do not override this method unless absolutely necessary. Too many separate tabs make the <i>Search Everywhere</i>
   * dialog unusable.</p>
   */
  default boolean isShownInSeparateTab() {
    return false;
  }

  /**
   * Return priority of found elements. Priority is used to sort items in results list.
   *
   * @deprecated method is left for backward compatibility only. If you want to consider elements weight in your search contributor
   * please use {@link WeightedSearchEverywhereContributor#fetchWeightedElements(String, ProgressIndicator, Processor)} method for fetching
   * this elements.
   */
  @Deprecated
  default int getElementPriority(@NotNull Item element, @NotNull String searchPattern) {
    return 0;
  }

  /**
   * <p>Returns list of commands supported by this contributor.</p>
   * <p>Usually commands are used for additional elements filtering, but you can implement any behavior for your commands.
   * {@link SearchEverywhereCommandInfo} doesn't contain any behavior details. All commands should be processed in
   * {@link SearchEverywhereContributor#fetchElements(String, ProgressIndicator, Processor)} method.</p>
   */
  default @NotNull List<SearchEverywhereCommandInfo> getSupportedCommands() {
    return Collections.emptyList();
  }

  /**
   * Return an advertisement text which can be shown in a right part of search field.
   */
  default @Nullable @Nls String getAdvertisement() { return null; }

  default @NotNull List<AnAction> getActions(@NotNull Runnable onChanged) {
    return Collections.emptyList();
  }

  /**
   * <p>Performs searching process. All found items will be passed to consumer.</p>
   * <p>Searching is performed until any of following events happens:
   * <ul>
   *   <li>all items which match {@code pattern} are found</li>
   *   <li>{@code progressIndicator} is cancelled</li>
   *   <li>{@code consumer} returns {@code false} for any item</li>
   * </ul></p>
   * @param pattern searching pattern used for matching
   * @param progressIndicator {@link ProgressIndicator} which can be used for tracking or cancelling searching process
   * @param consumer items {@link Processor} which will receive any found item. When false is returned by consumer this contributor stops
   *                 searching process
   */
  void fetchElements(@NotNull String pattern,
                     @NotNull ProgressIndicator progressIndicator,
                     @NotNull Processor<? super Item> consumer);

  /**
   * <p>Search for pattern matches with elements limit. Found items will be returned as {@link ContributorSearchResult} structure.</p>
   * <p>Searching is performed until any of following events happens:
   * <ul>
   *   <li>all items which match {@code pattern} are found</li>
   *   <li>{@code progressIndicator} is cancelled</li>
   *   <li>{@code elementsLimit} is reached</li>
   * </ul></p>
   *
   * @param pattern searching pattern used for matching
   * @param progressIndicator {@link ProgressIndicator} which can be used for tracking or cancelling searching process
   * @param elementsLimit maximal found items number
   */
  default @NotNull ContributorSearchResult<Item> search(@NotNull String pattern,
                                               @NotNull ProgressIndicator progressIndicator,
                                               int elementsLimit) {
    ContributorSearchResult.Builder<Item> builder = ContributorSearchResult.builder();
    fetchElements(pattern, progressIndicator, element -> {
      if (elementsLimit < 0 || builder.itemsCount() < elementsLimit) {
        builder.addItem(element);
        return true;
      }
      else {
        builder.setHasMore(true);
        return false;
      }
    });

    return builder.build();
  }

  /**
   * <p>Search for all pattern matches. Found items will be returned as {@link List}.</p>
   * <p>Searching is performed until any of following events happens:
   * <ul>
   *   <li>all items which match {@code pattern} are found</li>
   *   <li>{@code progressIndicator} is cancelled</li>
   * </ul></p>
   *
   * @param pattern searching pattern used for matching
   * @param progressIndicator {@link ProgressIndicator} which can be used for tracking or cancelling searching process
   */
  default @NotNull List<Item> search(@NotNull String pattern,
                            @NotNull ProgressIndicator progressIndicator) {
    List<Item> res = new ArrayList<>();
    fetchElements(pattern, progressIndicator, o -> res.add(o));
    return res;
  }

  /**
   * <p>Process selected item. Method called when user choose item from results list.</p>
   * <p>Returned result defines if dialog should be closed after processing element.</p>
   *
   * @param selected item chosen by user
   * @param modifiers keyboard modifiers (see {@link InputEvent#getModifiers()})
   * @param searchText text from search field
   *
   * @return {@code true} if dialog should be closed after element processing. {@code false} to let dialog be shown after processing
   */
  boolean processSelectedItem(@NotNull Item selected, int modifiers, @NotNull String searchText);

  /**
   * Creates {@link ListCellRenderer} for found items.
   */
  @NotNull
  ListCellRenderer<? super Item> getElementsRenderer();

  /**
   * Get context data for selected element.
   * @param element selected item
   * @param dataId {@link DataKey} ID
   *
   * @see DataKey
   * @see DataContext
   */
  @Nullable
  default Object getDataForItem(@NotNull Item element, @NotNull String dataId) {
    return null;
  }

  /**
   * String description for elements in suggestions list. One should check passed element explicitly for it's validity.
   */
  default @Nullable String getItemDescription(@NotNull Item element) {
    return null;
  }

  /**
   * Filter out special symbols from pattern before search.
   */
  default @NotNull String filterControlSymbols(@NotNull String pattern) {
    return pattern;
  }

  /**
   * <p>Defines if multi-selection should be supported in results list for items found by this contributor.</p>
   * <p>For example few classes can be simultaneously open from <i>Search Everywhere</i> (in different tabs). So classes contributor
   * supports multi-selection, but actions contributor doesn't since only one action can be performed at same time.</p>
   */
  default boolean isMultiSelectionSupported() {
    return false;
  }

  /**
   * Defines if this contributor allowed to call while indexing in process.
   */
  @Override
  default boolean isDumbAware() {
    return true;
  }

  /**
   * Defines if contributor should try to perform search with empty search pattern.
   */
  default boolean isEmptyPatternSupported() {
    return false;
  }

  @Override
  default void dispose() {}
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;

/**
 * @author gregsh
 */
public abstract class LRUPopupBuilder<T> {
  private static final int MAX_VISIBLE_SIZE = 20;
  private static final int LRU_ITEMS = 4;

  private final @PopupTitle String myTitle;
  private final PropertiesComponent myPropertiesComponent;
  private final Map<T, Pair<@Nls String, Icon>> myPresentations = new IdentityHashMap<>();

  private T mySelection;
  private Consumer<? super T> myOnChosen;
  private Comparator<? super T> myComparator;
  private Iterable<? extends T> myValues;
  private JBIterable<T> myTopValues = JBIterable.empty();
  private JBIterable<T> myMiddleValues = JBIterable.empty();
  private JBIterable<T> myBottomValues = JBIterable.empty();
  private Function<? super T, String> myExtraSpeedSearchNamer;

  public static @NotNull ListPopup forFileLanguages(@NotNull Project project,
                                                    @NotNull @PopupTitle String title,
                                                    @Nullable Language selection,
                                                    @NotNull Consumer<? super Language> onChosen) {
    return languagePopupBuilder(project, title, null).
      forValues(LanguageUtil.getFileLanguages()).
      withSelection(selection).
      onChosen(onChosen).
      buildPopup();
  }

  public static @NotNull LRUPopupBuilder<Language> languagePopupBuilder(@NotNull Project project,
                                                                        @NotNull @PopupTitle String title,
                                                                        @Nullable Function<? super Language, ? extends Icon> iconProvider) {
    return new LRUPopupBuilder<Language>(project, title) {
      @Override
      public String getDisplayName(Language language) {
        return language.getDisplayName();
      }

      @Override
      public Icon getIcon(Language language) {
        if (iconProvider != null) return iconProvider.apply(language);
        LanguageFileType fileType = language.getAssociatedFileType();
        Icon icon = fileType == null ? null : fileType.getIcon();
        return icon != null ? icon : AllIcons.FileTypes.Any_type;
      }

      @Override
      public String getStorageId(Language language) {
        return language.getID();
      }
    }.withComparator(LanguageUtil.LANGUAGE_COMPARATOR);
  }

  protected LRUPopupBuilder(@NotNull Project project, @NotNull @PopupTitle String title) {
    myTitle = title;
    myPropertiesComponent = PropertiesComponent.getInstance(project);
  }

  public abstract String getDisplayName(T t);
  public abstract String getStorageId(T t);
  public abstract Icon getIcon(T t);

  public @NotNull LRUPopupBuilder<T> forValues(@Nullable Iterable<? extends T> items) {
    myValues = items;
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withSelection(@Nullable T t) {
    mySelection = t;
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withExtraTopValue(@NotNull T extra, @Nls @NotNull String displayName, @Nullable Icon icon) {
    myTopValues = myTopValues.append(extra);
    myPresentations.put(extra, Pair.create(displayName, icon));
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withExtraMiddleValue(@NotNull T extra, @Nls @NotNull String displayName, @Nullable Icon icon) {
    myMiddleValues = myMiddleValues.append(extra);
    myPresentations.put(extra, Pair.create(displayName, icon));
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withExtraBottomValue(@NotNull T extra, @Nls @NotNull String displayName, @Nullable Icon icon) {
    myBottomValues = myBottomValues.append(extra);
    myPresentations.put(extra, Pair.create(displayName, icon));
    return this;
  }

  public @NotNull LRUPopupBuilder<T> onChosen(@Nullable Consumer<? super T> consumer) {
    myOnChosen = consumer;
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withComparator(@Nullable Comparator<? super T> comparator) {
    myComparator = comparator;
    return this;
  }

  public @NotNull LRUPopupBuilder<T> withExtraSpeedSearchNamer(@Nullable Function<? super T, String> function) {
    myExtraSpeedSearchNamer = function;
    return this;
  }

  public @NotNull ListPopup buildPopup() {
    List<String> ids = new ArrayList<>(restoreLRUItems());
    if (mySelection != null) {
      ids.add(getStorageId(mySelection));
    }
    List<T> topItems = myTopValues.toList();
    List<T> lru = new ArrayList<>(LRU_ITEMS);
    List<T> middleItems = myMiddleValues.toList();
    List<T> items = new ArrayList<>(MAX_VISIBLE_SIZE);
    List<T> bottomItems = myBottomValues.toList();
    for (T t : JBIterable.from(myValues)) {
      (ids.contains(getStorageId(t)) ? lru : items).add(t);
    }
    if (myComparator != null) {
      items.sort(myComparator);
    }
    if (!lru.isEmpty()) {
      lru.sort(Comparator.comparingInt(o -> ids.indexOf(getStorageId(o))));
    }
    List<T> combinedItems = ContainerUtil.concat(topItems, lru, middleItems, items, bottomItems);
    T sep1 = ContainerUtil.getOrElse(combinedItems, topItems.size() + lru.size() + middleItems.size(), null);
    T sep2 = ContainerUtil.getOrElse(combinedItems, topItems.size() + lru.size() + middleItems.size() + items.size(), null);

    BaseListPopupStep<T> step =
      new BaseListPopupStep<>(myTitle, combinedItems) {
        @Override
        public @NotNull String getTextFor(T t) {
          return t == null ? "" : getPresentation(t).first;
        }

        @Override
        public Icon getIconFor(T t) {
          return t == null ? null : getPresentation(t).second;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public String getIndexedString(T value) {
          String extra = myExtraSpeedSearchNamer != null ? StringUtil.nullize(myExtraSpeedSearchNamer.apply(value)) : null;
          if (extra == null) return super.getIndexedString(value);
          return super.getIndexedString(value) + SpeedSearchUtil.getDefaultHardSeparators() + extra;
        }

        @Override
        public PopupStep<?> onChosen(final T t, boolean finalChoice) {
          if (!bottomItems.contains(t) && !topItems.contains(t)) {
            storeLRUItems(t);
          }
          if (myOnChosen != null) {
            doFinalStep(() -> myOnChosen.consume(t));
          }
          return null;
        }

        @Override
        public @Nullable ListSeparator getSeparatorAbove(T value) {
          return value == sep1 || value == sep2 ? new ListSeparator() : null;
        }
      };
    int selection = Math.max(0, mySelection != null ? combinedItems.indexOf(mySelection) : 0);
    step.setDefaultOptionIndex(selection);

    return tweakSizeToPreferred(JBPopupFactory.getInstance().createListPopup(step));
  }

  private @NotNull Pair<@Nls String, Icon> getPresentation(T t) {
    Pair<String, Icon> p = myPresentations.get(t);
    if (p == null) myPresentations.put(t, p = Pair.create(getDisplayName(t), getIcon(t)));
    return p;
  }

  private static @NotNull ListPopup tweakSizeToPreferred(@NotNull ListPopup popup) {
    int nameLen = 0;
    ListPopupStep step = popup.getListStep();
    List values = step.getValues();
    for (Object v : values) {
      //noinspection unchecked
      nameLen = Math.max(nameLen, step.getTextFor(v).length());
    }
    if (values.size() > MAX_VISIBLE_SIZE) {
      Dimension size = new JLabel(StringUtil.repeatSymbol('a', nameLen), EmptyIcon.ICON_16, SwingConstants.LEFT).getPreferredSize(); //NON-NLS
      size.width += 20;
      size.height *= MAX_VISIBLE_SIZE;
      popup.setSize(size);
    }
    return popup;
  }

  private @NotNull List<String> restoreLRUItems() {
    return Objects.requireNonNullElse(myPropertiesComponent.getList(getLRUKey()), Collections.emptyList());
  }

  private void storeLRUItems(@NotNull T t) {
    List<String> values = myPropertiesComponent.getList(getLRUKey());
    List<String> lastUsed = new ArrayList<>(LRU_ITEMS);
    lastUsed.add(getStorageId(t));
    if (values != null) {
      for (String value : values) {
        if (!lastUsed.contains(value)) {
          lastUsed.add(value);
        }
        if (lastUsed.size() == LRU_ITEMS) {
          break;
        }
      }
    }
    myPropertiesComponent.setList(getLRUKey(), lastUsed);
  }


  private @NotNull String getLRUKey() {
    return getClass().getName() + "/" + myTitle;
  }
}

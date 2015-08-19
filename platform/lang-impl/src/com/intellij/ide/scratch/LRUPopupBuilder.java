/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.scratch;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author gregsh
 */
public abstract class LRUPopupBuilder<T> {

  private static final int MAX_VISIBLE_SIZE = 20;
  private static final int LRU_ITEMS = 4;

  private final String myTitle;
  private final PropertiesComponent myPropertiesComponent;
  private final Map<T, Pair<String, Icon>> myPresentations = ContainerUtil.newIdentityHashMap();

  private T mySelection;
  private Consumer<T> myOnChosen;
  private Comparator<? super T> myComparator;
  private Iterable<? extends T> myItemsIterable;
  private JBIterable<T> myExtraItems = JBIterable.empty();

  @NotNull
  public static ListPopup forFileLanguages(@NotNull Project project, @NotNull final Iterable<VirtualFile> files, @NotNull final PerFileMappings<Language> mappings) {
    return forFileLanguages(project, null, new Consumer<Language>() {
      @Override
      public void consume(Language t) {
        for (VirtualFile file : files) {
          mappings.setMapping(file, t);
        }
      }
    });
  }

  @NotNull
  public static ListPopup forFileLanguages(@NotNull Project project, @Nullable final Language selection, @NotNull final Consumer<Language> onChosen) {
    return languagePopupBuilder(project, "Languages").
      forValues(LanguageUtil.getFileLanguages()).
      withSelection(selection).
      onChosen(onChosen).
      buildPopup();
  }

  @NotNull
  public static LRUPopupBuilder<Language> languagePopupBuilder(@NotNull final Project project, @NotNull final String title) {
    return new LRUPopupBuilder<Language>(project, title) {
      @Override
      public String getDisplayName(Language language) {
        return language.getDisplayName();
      }

      @Override
      public Icon getIcon(Language language) {
        LanguageFileType associatedLanguage = language.getAssociatedFileType();
        return associatedLanguage != null ? associatedLanguage.getIcon() : null;
      }

      @Override
      public String getStorageId(Language language) {
        return language.getID();
      }
    }.withComparator(LanguageUtil.LANGUAGE_COMPARATOR);
  }

  protected LRUPopupBuilder(@NotNull Project project, @NotNull String title) {
    myTitle = title;
    myPropertiesComponent = PropertiesComponent.getInstance(project);
  }

  public abstract String getDisplayName(T t);
  public abstract String getStorageId(T t);
  public abstract Icon getIcon(T t);

  public LRUPopupBuilder<T> forValues(@Nullable Iterable<? extends T> items) {
    myItemsIterable = items;
    return this;
  }

  public LRUPopupBuilder<T> withSelection(@Nullable T t) {
    mySelection = t;
    return this;
  }

  public LRUPopupBuilder<T> withExtra(@NotNull T extra, @NotNull String displayName, @Nullable Icon icon) {
    myExtraItems = myExtraItems.append(extra);
    myPresentations.put(extra, Pair.create(displayName, icon));
    return this;
  }

  public LRUPopupBuilder<T> onChosen(@Nullable Consumer<T> consumer) {
    myOnChosen = consumer;
    return this;
  }

  public LRUPopupBuilder<T> withComparator(@Nullable Comparator<? super T> comparator) {
    myComparator = comparator;
    return this;
  }

  @NotNull
  public ListPopup buildPopup() {
    final List<String> ids = ContainerUtil.newArrayList(restoreLRUItems());
    if (mySelection != null) {
      ids.add(getStorageId(mySelection));
    }
    List<T> lru = ContainerUtil.newArrayListWithCapacity(LRU_ITEMS);
    List<T> items = ContainerUtil.newArrayListWithCapacity(MAX_VISIBLE_SIZE);
    final List<T> extra = myExtraItems.toList();
    for (T t : myItemsIterable) {
      (ContainerUtil.indexOf(ids, getStorageId(t)) != -1 ? lru : items).add(t);
    }
    if (myComparator != null) {
      Collections.sort(items, myComparator);
    }
    if (!lru.isEmpty()) {
      Collections.sort(lru, new Comparator<T>() {
        @Override
        public int compare(T o1, T o2) {
          return ContainerUtil.indexOf(ids, getStorageId(o1)) - ContainerUtil.indexOf(ids, getStorageId(o2));
        }
      });
    }
    final T separator1 = !lru.isEmpty() && !items.isEmpty()? items.get(0) : null;
    final T separator2 = !lru.isEmpty() || !items.isEmpty()? ContainerUtil.getFirstItem(extra) : null;

    final List<T> combinedItems = ContainerUtil.concat(lru, items, extra);
    BaseListPopupStep<T> step =
      new BaseListPopupStep<T>(myTitle, combinedItems) {
        @NotNull
        @Override
        public String getTextFor(@NotNull T t) {
          return getPresentation(t).first;
        }

        @Override
        public Icon getIconFor(@NotNull T t) {
          return getPresentation(t).second;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(final T t, boolean finalChoice) {
          if (!extra.contains(t)) {
            storeLRUItems(t);
          }
          if (myOnChosen != null) {
            EdtInvocationManager.getInstance().invokeLater(new Runnable() {
              @Override
              public void run() {
                myOnChosen.consume(t);
              }
            });
          }
          return null;
        }

        @Nullable
        @Override
        public ListSeparator getSeparatorAbove(T value) {
          return value == separator1 || value == separator2 ? new ListSeparator() : null;
        }
      };
    int selection = Math.max(0, mySelection != null ? combinedItems.indexOf(mySelection) : 0);
    step.setDefaultOptionIndex(selection);

    return tweakSizeToPreferred(JBPopupFactory.getInstance().createListPopup(step));
  }

  @NotNull
  private Pair<String, Icon> getPresentation(T t) {
    Pair<String, Icon> p = myPresentations.get(t);
    if (p == null) myPresentations.put(t, p = Pair.create(getDisplayName(t), getIcon(t)));
    return p;
  }

  @NotNull
  private static ListPopup tweakSizeToPreferred(@NotNull ListPopup popup) {
    int nameLen = 0;
    ListPopupStep step = popup.getListStep();
    List values = step.getValues();
    for (Object v : values) {
      //noinspection unchecked
      nameLen = Math.max(nameLen, step.getTextFor(v).length());
    }
    if (values.size() > MAX_VISIBLE_SIZE) {
      Dimension size = new JLabel(StringUtil.repeatSymbol('a', nameLen), EmptyIcon.ICON_16, SwingConstants.LEFT).getPreferredSize();
      size.width += 20;
      size.height *= MAX_VISIBLE_SIZE;
      popup.setSize(size);
    }
    return popup;
  }

  @NotNull
  private String[] restoreLRUItems() {
    return ObjectUtils.notNull(myPropertiesComponent.getValues(getLRUKey()), ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void storeLRUItems(@NotNull T t) {
    String[] values = myPropertiesComponent.getValues(getLRUKey());
    List<String> lastUsed = ContainerUtil.newArrayListWithCapacity(LRU_ITEMS);
    lastUsed.add(getStorageId(t));
    if (values != null) {
      for (String value : values) {
        if (!lastUsed.contains(value)) lastUsed.add(value);
        if (lastUsed.size() == LRU_ITEMS) break;
      }
    }
    myPropertiesComponent.setValues(getLRUKey(), ArrayUtil.toStringArray(lastUsed));
  }


  @NotNull
  private String getLRUKey() {
    return getClass().getName() + "/" + myTitle;
  }

}

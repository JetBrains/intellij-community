/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.frameworks.ui;

import com.intellij.facet.frameworks.LibrariesDownloadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.SmartList;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated use {@link com.intellij.util.download.DownloadableFileService} for asynchronous fetching of available versions
 */
@Deprecated
public class LibrariesDownloadUiUtil {

  private LibrariesDownloadUiUtil() {
  }

  public static JComboBox createVersionsCombobox(@NotNull final String groupId, final URL... localUrls) {
    final JComboBox jComboBox = new JComboBox();

    initAsyncComboBoxModel(jComboBox, groupId, localUrls);

    return jComboBox;
  }

  public static void initAsyncComboBoxModel(@NotNull final JComboBox jComboBox,
                                            @NotNull final String groupId,
                                            final URL... localUrls) {
    final List<Object> items = new ArrayList<>();

    new UiNotifyConnector.Once(jComboBox, new Activatable() {
      @Override
      public void showNotify() {
        loadItems(jComboBox, items, groupId, localUrls);
      }

      @Override
      public void hideNotify() {
      }
    });

    items.add("loading...");
    jComboBox.setModel(new CollectionComboBoxModel(items, items.get(0)));
    jComboBox.setEnabled(false);
  }

  private static void loadItems(@NotNull final JComboBox jComboBox,
                                final List<Object> items,
                                final String groupId,
                                final URL... localUrls) {
    final ModalityState state = ModalityState.current();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final List<Object> newItems = new SmartList<>(LibrariesDownloadAssistant.getVersions(groupId, localUrls));

      ApplicationManager.getApplication().invokeLater(() -> {
        items.clear();
        if (!newItems.isEmpty()) {
          items.addAll(newItems);
          final CollectionComboBoxModel model = (CollectionComboBoxModel)jComboBox.getModel();
          model.update();
          jComboBox.setSelectedIndex(0);
        }
        jComboBox.setEnabled(true);
      }, state);
    }

    );
  }
}

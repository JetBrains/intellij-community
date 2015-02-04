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
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.tools.util.EditorsDiffPanelBase;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BinaryDiffPanel extends EditorsDiffPanelBase {
  @NotNull protected final JPanel INSERTED_CONTENT_NOTIFICATION =
    createNotification("Content added", TextDiffType.INSERTED.getColor(null));
  @NotNull protected final JPanel REMOVED_CONTENT_NOTIFICATION =
    createNotification("Content removed", TextDiffType.DELETED.getColor(null));

  @NotNull private final BinaryDiffViewer myViewer;

  public BinaryDiffPanel(@NotNull BinaryDiffViewer viewer,
                         @NotNull BinaryContentPanel editorsPanel,
                         @NotNull DataProvider dataProvider,
                         @NotNull DiffContext context) {
    super(editorsPanel, dataProvider, context);
    myViewer = viewer;
  }

  @Nullable
  @Override
  protected JComponent getCurrentEditor() {
    FileEditor editor = myViewer.getCurrentEditor();
    return editor != null ? editor.getComponent() : null;
  }

  public void addInsertedContentNotification() {
    myNotificationsPanel.add(INSERTED_CONTENT_NOTIFICATION);
    myNotificationsPanel.revalidate();
  }

  public void addRemovedContentNotification() {
    myNotificationsPanel.add(REMOVED_CONTENT_NOTIFICATION);
    myNotificationsPanel.revalidate();
  }
}

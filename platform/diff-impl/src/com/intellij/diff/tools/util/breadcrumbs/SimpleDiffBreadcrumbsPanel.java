// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SimpleDiffBreadcrumbsPanel extends DiffBreadcrumbsPanel {
  private final VirtualFile myFile;

  public SimpleDiffBreadcrumbsPanel(@NotNull Editor editor, @NotNull Disposable disposable) {
    super(editor, disposable);

    myFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
  }

  @Override
  protected boolean updateCollectors(boolean enabled) {
    return enabled && findCollector(myFile) != null;
  }

  @Nullable
  @Override
  protected Iterable<? extends Crumb> computeCrumbs(int offset) {
    FileBreadcrumbsCollector collector = findCollector(myFile);
    if (collector == null) return null;

    Document document = myEditor.getDocument();
    return collector.computeCrumbs(myFile, document, offset, null);
  }
}

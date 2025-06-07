// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nullable VirtualFile myFile;

  // effectively nullable: The 'com.intellij.xml.breadcrumbs.BreadcrumbsPanel.<init>' may synchronously execute update in tests
  private final @Nullable DiffBreadcrumbCollectorHolder myCollectorHolder;

  public SimpleDiffBreadcrumbsPanel(@NotNull Editor editor, @NotNull Disposable disposable) {
    super(editor, disposable);

    myFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    myCollectorHolder = new DiffBreadcrumbCollectorHolder();
  }

  @Override
  protected boolean updateCollectors(boolean enabled) {
    return myCollectorHolder != null && myCollectorHolder.update(myFile, enabled);
  }

  @Override
  protected @Nullable Iterable<? extends Crumb> computeCrumbs(int offset) {
    VirtualFile file = myFile;
    FileBreadcrumbsCollector collector = myCollectorHolder != null ? myCollectorHolder.getBreadcrumbsCollector() : null;
    if (file == null || collector == null) return null;

    Document document = myEditor.getDocument();
    return collector.computeCrumbs(file, document, offset, null);
  }
}

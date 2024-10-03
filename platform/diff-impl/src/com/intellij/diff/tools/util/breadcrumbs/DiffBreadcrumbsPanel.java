// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xml.breadcrumbs.BreadcrumbsPanel;
import com.intellij.xml.breadcrumbs.BreadcrumbsUtilEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class DiffBreadcrumbsPanel extends BreadcrumbsPanel {
  private boolean myCrumbsShown;

  public DiffBreadcrumbsPanel(@NotNull Editor editor, @NotNull Disposable disposable) {
    super(editor);
    Disposer.register(disposable, this);

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        updateVisibility();
      }
    });
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> updateVisibility());
  }

  public void setCrumbsShown(boolean value) {
    myCrumbsShown = value;
    updateVisibility();
  }

  private void updateVisibility() {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.stateForComponent(this), () -> {
      if (Disposer.isDisposed(this)) return;

      boolean hasCollectors = updateCollectors(myCrumbsShown);
      if (hasCollectors != isVisible()) {
        setVisible(hasCollectors);
        revalidate();
        repaint();
      }
      queueUpdate();
    });
  }

  protected abstract boolean updateCollectors(boolean enabled);

  @Nullable
  protected FileBreadcrumbsCollector findCollector(@Nullable VirtualFile file) {
    if (file == null) return null;

    FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(file);
    if (viewProvider == null) return null;

    if (!ContainerUtil.exists(viewProvider.getLanguages(),
                              lang -> BreadcrumbsUtilEx.isBreadcrumbsShownFor(lang) && BreadcrumbsUtil.getInfoProvider(lang) != null)) {
      return null;
    }

    return findCollectorFor(myProject, file, this);
  }

  @Override
  protected int getLeftOffset() {
    if (((EditorEx)myEditor).getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT) {
      return 0;
    }
    return super.getLeftOffset();
  }
}

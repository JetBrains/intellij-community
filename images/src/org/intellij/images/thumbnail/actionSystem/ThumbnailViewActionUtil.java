// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NotNull;

/**
 * Thumbnail view actions utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ThumbnailViewActionUtil {
  private ThumbnailViewActionUtil() {
  }

  /**
   * Extract current thumbnail view from event context.
   *
   * @param e Action event
   * @return Current {@link ThumbnailView} or {@code null}
   */
  public static ThumbnailView getVisibleThumbnailView(@NotNull AnActionEvent e) {
    ThumbnailView thumbnailView = getThumbnailView(e);
    if (thumbnailView != null && thumbnailView.isVisible()) {
      return thumbnailView;
    }
    return null;
  }

  public static ThumbnailView getThumbnailView(@NotNull AnActionEvent e) {
    return e.getData(ThumbnailView.DATA_KEY);
  }

  /**
   * Enable or disable current action from event.
   *
   * @param e Action event
   * @return Enabled value
   */
  public static boolean setEnabled(@NotNull AnActionEvent e) {
    ThumbnailView thumbnailView = getVisibleThumbnailView(e);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(thumbnailView != null);
    return presentation.isEnabled();
  }
}

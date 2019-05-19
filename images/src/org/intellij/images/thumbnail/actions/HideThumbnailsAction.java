// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Hide tool window.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class HideThumbnailsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        if (view != null) {
            view.setVisible(false);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        ThumbnailViewActionUtil.setEnabled(e);
    }
}

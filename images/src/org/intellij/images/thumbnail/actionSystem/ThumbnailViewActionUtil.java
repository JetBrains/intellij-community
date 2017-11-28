/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.images.thumbnail.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.thumbnail.ThumbnailView;

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
     * @return Current {@link org.intellij.images.thumbnail.ThumbnailView} or {@code null}
     */
    public static ThumbnailView getVisibleThumbnailView(AnActionEvent e) {
        ThumbnailView thumbnailView = getThumbnailView(e);
        if (thumbnailView != null && thumbnailView.isVisible()) {
            return thumbnailView;
        }
        return null;
    }

    public static ThumbnailView getThumbnailView(AnActionEvent e) {
      return ThumbnailView.DATA_KEY.getData(e.getDataContext());
    }

    /**
     * Enable or disable current action from event.
     *
     * @param e Action event
     * @return Enabled value
     */
    public static boolean setEnabled(AnActionEvent e) {
        ThumbnailView thumbnailView = getVisibleThumbnailView(e);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(thumbnailView != null);
        return presentation.isEnabled();
    }
}

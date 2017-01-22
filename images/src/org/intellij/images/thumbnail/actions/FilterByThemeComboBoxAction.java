/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/** $Id$ */

package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class FilterByThemeComboBoxAction extends ComboBoxAction {
    
    public void update(final AnActionEvent e) {
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        ThemeFilter[] extensions = ThemeFilter.EP_NAME.getExtensions();
        e.getPresentation().setVisible(view != null && extensions.length > 0);
        if (view != null) {
            ThemeFilter filter = view.getFilter();
            e.getPresentation().setText(filter == null ? "All" : filter.getDisplayName());
        }
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new FilterByThemeAction(new ThemeFilter() {
            @Override
            public String getDisplayName() {
                return "All";
            }

            @Override
            public boolean accepts(VirtualFile file) {
                return true;
            }
        }));
        for (ThemeFilter filter : ThemeFilter.EP_NAME.getExtensions()) {
            group.add(new FilterByThemeAction(filter));
        }

        return group;
    }
}

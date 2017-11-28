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
package org.intellij.images.editor.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.ui.ImageComponentDecorator;

/**
 * Editor actions utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ImageEditorActionUtil {
    private ImageEditorActionUtil() {
    }

    /**
     * Extract current editor from event context.
     *
     * @param e Action event
     * @return Current {@link ImageEditor} or {@code null}
     */
    //public static ImageEditor getValidEditor(AnActionEvent e) {
    //    ImageEditor editor = getEditor(e);
    //    if (editor != null && editor.isValid()) {
    //        return editor;
    //    }
    //    return null;
    //}

    public static ImageComponentDecorator getImageComponentDecorator(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        return ImageComponentDecorator.DATA_KEY.getData(dataContext);
    }

    /**
     * Enable or disable current action from event.
     *
     * @param e Action event
     * @return Enabled value
     */
    public static boolean setEnabled(AnActionEvent e) {
        ImageComponentDecorator decorator = getImageComponentDecorator(e);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(decorator != null);
        return presentation.isEnabled();
    }
}

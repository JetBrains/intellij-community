/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.lang.regexp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;

public class RegExpSupportLoader implements ApplicationComponent {
    // should be true unless verified fix for IDEADEV-10862 is available 
    private static final boolean DBG_MODE = true || Boolean.getBoolean("regexp-lang.register-file-type");

    public static final RegExpLanguage LANGUAGE = RegExpLanguage.INSTANCE;
    public static final RegExpFileType FILE_TYPE = RegExpFileType.INSTANCE;

    public void initComponent() {
        if (DBG_MODE) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                    final String[] extensions = new String[]{ FILE_TYPE.getDefaultExtension() };
                    FileTypeManager.getInstance().registerFileType(FILE_TYPE, extensions);
                }
            });
        }
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "RegExpSupportLoader";
    }
}

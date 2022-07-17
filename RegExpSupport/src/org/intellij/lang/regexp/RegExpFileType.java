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

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RegExpFileType extends LanguageFileType {
    public static final RegExpFileType INSTANCE = new RegExpFileType();

    private RegExpFileType() {
        super(RegExpLanguage.INSTANCE);
    }

    private RegExpFileType(@NotNull Language language) {
        super(language);
        if (!(language.getBaseLanguage() instanceof RegExpLanguage)) {
            throw new IllegalArgumentException(String.valueOf(language.getBaseLanguage()));
        }
    }

    @Override
    @NotNull
    @NonNls
    public String getName() {
        return "RegExp";
    }

    @Override
    @NotNull
    public String getDescription() {
        return RegExpBundle.message("filetype.regular.expression.description");
    }

    @Override
    @NotNull
    @NonNls
    public String getDefaultExtension() {
        return "regexp";
    }

    @Override
    public Icon getIcon() {
        return getLanguage() == RegExpLanguage.INSTANCE ? AllIcons.FileTypes.Regexp : null;
    }

    @NotNull
    public static LanguageFileType forLanguage(@NotNull Language language) {
        return new RegExpFileType(language);
    }
}

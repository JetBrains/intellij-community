/*
 Copyright 2020 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin;

import javax.swing.*;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffFileType extends LanguageFileType {
    public static final DiffFileType INSTANCE = new DiffFileType();

    private DiffFileType() {
        super(DiffLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Diff/Patch";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Diff/Patch";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "diff";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return DiffIcons.FILE;
    }
}

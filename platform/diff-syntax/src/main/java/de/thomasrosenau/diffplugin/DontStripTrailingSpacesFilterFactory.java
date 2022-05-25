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

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class DontStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
    @NotNull
    @Override
    protected PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
        return new PsiBasedStripTrailingSpacesFilter(document) {
            @Override
            public boolean isStripSpacesAllowedForLine(int line) {
                return false;
            }

            @Override
            protected void process(@NotNull PsiFile psiFile) {
                // do nothing
            }
        };
    }


    @Override
    protected boolean isApplicableTo(@NotNull Language language) {
        return language.is(DiffLanguage.INSTANCE);
    }
}

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

public class DiffLanguage extends Language {
    /*
    public static final DiffLanguage INSTANCE = new DiffLanguage();
    // This throws an exception in the folding tests
    */

    // Workaround for the above
    public static final DiffLanguage INSTANCE;

    static {
        DiffLanguage instance;
        try {
            instance = new DiffLanguage();
        } catch (RuntimeException e) {
            instance = findInstance(DiffLanguage.class);
        }
        INSTANCE = instance;
    }
    // end Workaround


    private DiffLanguage() {
        super("Diff");
    }
}

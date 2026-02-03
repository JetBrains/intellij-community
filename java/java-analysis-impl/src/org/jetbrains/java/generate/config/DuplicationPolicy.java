/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package org.jetbrains.java.generate.config;

import com.intellij.java.analysis.JavaAnalysisBundle;
import org.jetbrains.annotations.Nls;

import java.util.function.Supplier;

public enum DuplicationPolicy {
    ASK(JavaAnalysisBundle.messagePointer("duplication.policy.ask")),
    REPLACE(JavaAnalysisBundle.messagePointer("duplication.policy.replace")),
    DUPLICATE(JavaAnalysisBundle.messagePointer("duplication.policy.generate.duplicate"));

    private final Supplier<@Nls String> displayName;

    DuplicationPolicy(Supplier<@Nls String> displayName) {
        this.displayName = displayName;
    }

    @Override
    public @Nls String toString() {
        return displayName.get();
    }
}

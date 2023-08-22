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
import org.jetbrains.annotations.PropertyKey;

public enum InsertWhere {
    AT_CARET("generate.members.position.at.caret"),
    AFTER_EQUALS_AND_HASHCODE("generate.members.position.after.equals.and.hashcode"),
    AT_THE_END_OF_A_CLASS("generate.members.position.at.the.end.of.class");

    private final @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String propertyKey;

    InsertWhere(@PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String propertyKey) {
        this.propertyKey = propertyKey;
    }

    @Override
    public @Nls String toString() {
        return JavaAnalysisBundle.message(propertyKey);
    }
}

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
package org.intellij.lang.regexp.psi;

import org.jetbrains.annotations.NotNull;

public interface RegExpQuantifier extends RegExpAtom {

    @NotNull
    RegExpAtom getAtom();

    /**
     * The min,max occurrence count the quantifier represents. This is either an instance of
     * {@link org.intellij.lang.regexp.psi.RegExpQuantifier.SimpleCount} for the ?, * or +
     * quantifiers, or an arbitrary instance of the {@link org.intellij.lang.regexp.psi.RegExpQuantifier.Count}
     * interface that returns the values obtained from the {min,max} quantifier.
     */
    @NotNull
    Count getCount();

    /**
     * The "greedyness" type of the quantifier.  
     */
    @NotNull
    Type getType();

    interface Count {
        @NotNull
        String getMin();
        @NotNull
        String getMax();
    }

    enum SimpleCount implements Count {
        /** ? */
        ONE_OR_ZERO("0", "1"),
        /** * */
        ZERO_OR_MORE("0", ""),
        /** + */
        ONE_OR_MORE("1", "");

        private final String myMin;
        private final String myMax;

        SimpleCount(String min, String max) {
            myMin = min;
            myMax = max;
        }

        @NotNull
        public String getMin() {
            return myMin;
        }
        @NotNull
        public String getMax() {
            return myMax;
        }
    }

    enum Type {
        GREEDY(""),
        /** ? */
        RELUCTANT("?"),
        /** + */
        POSSESSIVE("+");

        private final String myToken;
        Type(String ch) {
            myToken = ch;
        }
        public String getToken() {
            return myToken;
        }
    }
}

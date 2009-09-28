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
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpSimpleClass;

public class RegExpSimpleClassImpl extends RegExpElementImpl implements RegExpSimpleClass {
    public RegExpSimpleClassImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public Kind getKind() {
        final String s = getUnescapedText();
        if (s.equals(".")) {
            return Kind.ANY;
        } else if (s.equals("\\d")) {
            return Kind.DIGIT;
        } else if (s.equals("\\D")) {
            return Kind.NON_DIGIT;
        } else if (s.equals("\\w")) {
            return Kind.WORD;
        } else if (s.equals("\\W")) {
            return Kind.NON_WORD;
        } else if (s.equals("\\s")) {
            return Kind.SPACE;
        } else if (s.equals("\\S")) {
            return Kind.NON_SPACE;
        }
        assert false;
        return null;
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitSimpleClass(this);
    }
}

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

import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.psi.impl.RegExpOptionsImpl;

public class RegExpElementVisitor extends PsiElementVisitor {

    public void visitRegExpElement(RegExpElement element) {
    }

    public void visitRegExpChar(RegExpChar ch) {
        visitRegExpElement(ch);
    }

    public void visitRegExpCharRange(RegExpCharRange range) {
        visitRegExpElement(range);
    }

    public void visitSimpleClass(RegExpSimpleClass simpleClass) {
        visitRegExpElement(simpleClass);
    }

    public void visitRegExpClass(RegExpClass expClass) {
        visitRegExpElement(expClass);
    }

    public void visitRegExpGroup(RegExpGroup group) {
        visitRegExpElement(group);
    }

    public void visitRegExpOptions(RegExpOptionsImpl options) {
        visitRegExpElement(options);
    }

    public void visitRegExpProperty(RegExpProperty property) {
        visitRegExpElement(property);
    }

    public void visitRegExpBranch(RegExpBranch branch) {
        visitRegExpElement(branch);
    }

    public void visitRegExpPattern(RegExpPattern pattern) {
        visitRegExpElement(pattern);
    }

    public void visitRegExpBackref(RegExpBackref backref) {
        visitRegExpElement(backref);
    }

    public void visitRegExpClosure(RegExpClosure closure) {
        visitRegExpElement(closure);
    }

    public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
        visitRegExpElement(quantifier);
    }

    public void visitRegExpBoundary(RegExpBoundary boundary) {
        visitRegExpElement(boundary);
    }

    public void visitRegExpSetOptions(RegExpSetOptions options) {
        visitRegExpElement(options);
    }

    public void visitRegExpIntersection(RegExpIntersection intersection) {
        visitRegExpElement(intersection);
    }

    public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
        visitRegExpElement(groupRef);
    }

    public void visitRegExpPyCondRef(RegExpPyCondRef condRef) {
        visitRegExpElement(condRef);
    }

    public void visitPosixBracketExpression(RegExpPosixBracketExpression posixBracketExpression) {
        visitRegExpElement(posixBracketExpression);
    }
}

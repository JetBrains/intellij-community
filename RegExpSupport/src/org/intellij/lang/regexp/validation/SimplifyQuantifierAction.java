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
package org.intellij.lang.regexp.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.psi.RegExpClosure;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.jetbrains.annotations.NotNull;

class SimplifyQuantifierAction implements IntentionAction {
    private final RegExpQuantifier myQuantifier;
    private final String myReplacement;

    public SimplifyQuantifierAction(RegExpQuantifier quantifier, String s) {
        myQuantifier = quantifier;
        myReplacement = s;
    }

    @NotNull
    public String getText() {
        return myReplacement == null ? "Simplify" : "Replace with '" + myReplacement + "'";
    }

    @NotNull
    public String getFamilyName() {
        return "Simplify Quantifier";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return myQuantifier.isValid();
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (myReplacement == null) {
            myQuantifier.delete();
        } else {
            final PsiFileFactory factory = PsiFileFactory.getInstance(project);

            final PsiFile f = factory.createFileFromText("dummy.regexp", RegExpFileType.INSTANCE, "a" + myReplacement + myQuantifier.getType().getToken());
            final RegExpPattern pattern = PsiTreeUtil.getChildOfType(f, RegExpPattern.class);
            assert pattern != null;

            final RegExpClosure closure = (RegExpClosure)pattern.getBranches()[0].getAtoms()[0];
            myQuantifier.replace(closure.getQuantifier());
        }
    }

    public boolean startInWriteAction() {
        return true;
    }
}

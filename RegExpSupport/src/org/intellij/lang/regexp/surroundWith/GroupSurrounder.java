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
package org.intellij.lang.regexp.surroundWith;

import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpFileType;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class GroupSurrounder implements Surrounder {
    private final String myTitle;
    private final String myGroupStart;

    public GroupSurrounder(String title, String groupStart) {
        myTitle = title;
        myGroupStart = groupStart;
    }

    public String getTemplateDescription() {
        return myTitle;
    }

    public boolean isApplicable(@NotNull PsiElement[] elements) {
        return elements.length == 1 || PsiTreeUtil.findCommonParent(elements) == elements[0].getParent();
    }

    @Nullable
    public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
        assert elements.length == 1 || PsiTreeUtil.findCommonParent(elements) == elements[0].getParent();
        final PsiElement e = elements[0];
        final ASTNode node = e.getNode();
        assert node != null;

        final ASTNode parent = node.getTreeParent();

        final StringBuilder s = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            final PsiElement element = elements[i];
            if (element instanceof RegExpElementImpl) {
                s.append(((RegExpElementImpl)element).getUnescapedText());
            } else {
                s.append(element.getText());
            }
            if (i > 0) {
                final ASTNode child = element.getNode();
                assert child != null;
                parent.removeChild(child);
            }
        }
        final PsiFileFactory factory = PsiFileFactory.getInstance(project);

        final PsiFile f = factory.createFileFromText("dummy.regexp", RegExpFileType.INSTANCE, makeReplacement(s));
        final RegExpPattern pattern = PsiTreeUtil.getChildOfType(f, RegExpPattern.class);
        assert pattern != null;

        final RegExpAtom element = pattern.getBranches()[0].getAtoms()[0];

        if (isInsideStringLiteral(e)) {
            final Document doc = editor.getDocument();
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
            final TextRange tr = e.getTextRange();
            doc.replaceString(tr.getStartOffset(), tr.getEndOffset(),
                    StringUtil.escapeStringCharacters(element.getText()));

            return TextRange.from(e.getTextRange().getEndOffset(), 0);
        } else {
            final PsiElement n = e.replace(element);
            return TextRange.from(n.getTextRange().getEndOffset(), 0);
        }
    }

    private static boolean isInsideStringLiteral(PsiElement context) {
      while (context != null) {
        if (RegExpElementImpl.isLiteralExpression(context)) {
          return true;
        }
        context = context.getContext();
      }
      return false;
    }

    protected String makeReplacement(StringBuilder s) {
        return myGroupStart + s + ")";
    }
}

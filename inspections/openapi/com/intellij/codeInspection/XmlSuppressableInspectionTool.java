/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + XmlSuppressableInspectionTool.class.getName());
  @NonNls private static final String SUPPRESS_PREFIX = "<!--suppress ";
  @NonNls private static final String SUPPRESS_SUFFIX = " -->\n";
  @NonNls private static final String ALL = "ALL";

  public SuppressIntentionAction[] getSuppressActions() {
    return new SuppressIntentionAction[]{new SuppressTag(), new SuppressForFile(), new SuppressAllForFile()};
  }

  public boolean isSuppressedFor(final PsiElement element) {
    XmlTag tag = PsiTreeUtil.getContextOfType(element, XmlTag.class, false);
    if (tag == null) return false;

    PsiElement prev = tag.getPrevSibling();
    while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();

    while (prev instanceof PsiComment || prev instanceof XmlProlog  || prev instanceof XmlText) {
      @NonNls String text = prev.getText();
      if (isSuppressedFor(text)) return true;
      prev = prev.getPrevSibling();
    }

    final XmlFile file = (XmlFile)tag.getContainingFile();
    final XmlDocument document = file.getDocument();
    final XmlTag rootTag = document != null ? document.getRootTag() : null;

    PsiElement leaf = rootTag != null ? rootTag.getPrevSibling() : file.findElementAt(0);

    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getPrevSibling();

    while (leaf instanceof PsiComment || leaf instanceof XmlProlog || leaf instanceof XmlText) {
      @NonNls String text = leaf.getText();
      if (isSuppressedFor(text)) return true;
      leaf = leaf.getPrevSibling();
    }

    return false;
  }

  private boolean isSuppressedFor(@NonNls final String text) {
    @NonNls final String[] parts = text.split("[ ,]");
    return text.contains(SUPPRESS_PREFIX) && (ArrayUtil.find(parts, getID()) != -1 || ArrayUtil.find(parts, ALL) != -1);
  }

  private void suppress(PsiFile file, @Nullable XmlTag rootTag) {
    suppress(file, rootTag, SUPPRESS_PREFIX + getID() + SUPPRESS_SUFFIX, new Function<String, String>() {
      public String fun(final String text) {
        return text.replaceAll(SUPPRESS_SUFFIX, ", " + getID() + SUPPRESS_SUFFIX);
      }
    });
  }

  private static void suppress(PsiFile file, @Nullable XmlTag rootTag, String suppressComment, Function<String, String> replace) {
    final Project project = file.getProject();
    if (ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(file.getVirtualFile()).hasReadonlyFiles()) {
      return;
    }
    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
    LOG.assertTrue(doc != null);

    PsiElement leaf = rootTag != null ? rootTag.getPrevSibling() : file.findElementAt(0);

    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getPrevSibling();

    while (leaf instanceof PsiComment || leaf instanceof XmlProlog || leaf instanceof XmlText) {
      @NonNls String text = leaf.getText();
      if (text.contains(SUPPRESS_PREFIX)) {
        final TextRange textRange = leaf.getTextRange();
        doc.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), replace.fun(text));
        return;
      }
      leaf = leaf.getPrevSibling();
    }

    final int offset = rootTag != null ? rootTag.getTextRange().getStartOffset() : 0;
    doc.insertString(offset, suppressComment);
    CodeStyleManager.getInstance(project).adjustLineIndent(doc, offset + suppressComment.length());
    UndoUtil.markPsiFileForUndo(file);
  }

  public class SuppressTag extends SuppressIntentionAction {

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, XmlTag.class) != null;
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      suppress(element.getContainingFile(), tag);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  public class SuppressForFile extends SuppressIntentionAction{

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.file.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      final PsiFile file = element.getContainingFile();
      final XmlDocument document = ((XmlFile)file).getDocument();
      suppress(file, document != null ? document.getRootTag() : null);
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
      return element != null && element.isValid() && element.getContainingFile() instanceof XmlFile;
    }


    public boolean startInWriteAction() {
      return true;
    }
  }

  public static class SuppressAllForFile extends SuppressIntentionAction{
    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.all.for.file.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
      return element != null && element.isValid() && element.getContainingFile() instanceof XmlFile;
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      final PsiFile file = element.getContainingFile();
      final XmlDocument document = ((XmlFile)file).getDocument();
      final XmlTag rootTag = document != null ? document.getRootTag() : null;
      final String suppressComment = SUPPRESS_PREFIX + ALL + SUPPRESS_SUFFIX;
      suppress(file, rootTag, suppressComment, new Function<String, String>() {
        public String fun(final String s) {
          return s.substring(0, s.indexOf(SUPPRESS_PREFIX)) + suppressComment;
        }
      });
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
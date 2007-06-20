package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.undo.UndoManager;
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

public abstract class XmlSuppressableInspectionTool extends CustomSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + XmlSuppressableInspectionTool.class.getName());
  @NonNls private static final String SUPPRESS_PREFIX = "<!--suppress ";
  @NonNls private static final String SUPPRESS_SUFFIX = " -->\n";
  @NonNls private static final String ALL = "ALL";

  public IntentionAction[] getSuppressActions(final PsiElement element) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    if (tag != null) {
      return new IntentionAction[]{new SuppressTag(tag), new SuppressForFile(), new SuppressAllForFile()};
    }
    return new IntentionAction[]{new SuppressForFile(), new SuppressAllForFile()};
  }

  public boolean isSuppressedFor(final PsiElement element) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
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
    UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
  }

  public class SuppressTag implements IntentionAction {
    private XmlTag myTag;

    public SuppressTag(final XmlTag tag) {
      myTag = tag;
    }

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
      return myTag != null && myTag.isValid();
    }

    public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      suppress(file, myTag);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  public class SuppressForFile implements IntentionAction {

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.file.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
      return file instanceof XmlFile && file.isValid();
    }

    public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      final XmlDocument document = ((XmlFile)file).getDocument();
      suppress(file, document != null ? document.getRootTag() : null);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  public static class SuppressAllForFile implements IntentionAction {
    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.all.for.file.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
      return file instanceof XmlFile && file.isValid();
    }

    public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavadocAutoLookupHandler;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.position.SuperParentFilter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaTypedHandler extends TypedHandlerDelegate {
  private boolean myJavaLTTyped;

  public boolean checkAutoPopup(final char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (charTyped == '@' && file instanceof PsiJavaFile) {
      autoPopupJavadocLookup(project, editor);
      return true;
    }
    if (charTyped == '#' || charTyped == '.') {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, new MemberAutoLookupCondition());
      return true;
    }

    return false;
  }

  public boolean beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    final FileType originalFileType = getOriginalFileType(file);

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    file instanceof PsiJavaFile &&
                    !(file instanceof JspFile) &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0 &&
                    BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(offsetBefore, editor);

    if ('>' == c) {
      if (file instanceof PsiJavaFile && !(file instanceof JspFile) &&
          CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
               ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
        if (handleJavaGT(editor)) return true;
      }
    }

    if (originalFileType == StdFileTypes.JAVA && c == '}') {
      if (handleJavaArrayInitializerRBrace(editor)) return true;
    }
    if (c == ';') {
      if (handleSemicolon(editor, fileType)) return true;
    }
    return false;
  }

  public boolean charTyped(final char c, final Project project, final Editor editor, final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      handleAfterJavaLT(editor);
      return true;
    }
    final FileType type = getOriginalFileType(file);
    if (type == StdFileTypes.JAVA && c == '{') {
      if (handleJavaArrayInitializerLBrace(editor)) return true;
    }
    return false;
  }

  @Nullable
  private static FileType getOriginalFileType(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null ? virtualFile.getFileType() : null;
  }

  private static boolean handleSemicolon(Editor editor, FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private static boolean handleJavaArrayInitializerLBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset - 1);
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getDocument().insertString(offset, "}");
    return true;
  }

  private static boolean handleJavaArrayInitializerRBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getStart() == 0 || iterator.getTokenType() != JavaTokenType.RBRACE) return false;
    iterator.retreat();
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private static boolean checkArrayInitializerLBrace(final HighlighterIterator iterator) {
    int lbraceCount = 0;
    while(iterator.getTokenType() == JavaTokenType.LBRACE) {
      lbraceCount++;
      iterator.retreat();
    }
    if (lbraceCount == 0) return false;
    if (iterator.getTokenType() == JavaTokenType.WHITE_SPACE) iterator.retreat();
    for(int i=0; i<lbraceCount; i++) {
      if (iterator.getTokenType() != JavaTokenType.RBRACKET) return false;
      iterator.retreat();
      if (iterator.getTokenType() != JavaTokenType.LBRACKET) return false;
      iterator.retreat();
    }
    return true;
  }

  //need custom handler, since brace matcher cannot be used
  private static boolean handleJavaGT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getTokenType() != JavaTokenType.GT) return false;
    while (!iterator.atEnd() && !JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd()) return false;
    if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.retreat();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance--;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance++;
      }
      else if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.retreat();
    }

    if (balance == 0) {
      editor.getCaretModel().moveToOffset(offset + 1);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  //need custom handler, since brace matcher cannot be used
  private static void handleAfterJavaLT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance++;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance--;
      }
      else if (JavaTypedHandlerUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance == 1) {
      editor.getDocument().insertString(offset, ">");
    }
  }

  private static void autoPopupJavadocLookup(final Project project, final Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_JAVADOC_LOOKUP) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              public void run(){
                new JavadocAutoLookupHandler().invoke(project, editor, file);
              }
            },
            "",
            null
          );
        }
      };
      AutoPopupController.getInstance(project).invokeAutoPopupRunnable(request, settings.JAVADOC_LOOKUP_DELAY);
    }
  }

  private static class MemberAutoLookupCondition implements Condition<Editor> {
    public boolean value(final Editor editor) {
      final Project project = editor.getProject();
      if (project == null) return false;
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return false;
      int offset = editor.getCaretModel().getOffset();

      PsiElement lastElement = file.findElementAt(offset - 1);
      if (lastElement == null) {
        return false;
      }

      //do not show lookup when typing varargs ellipsis
      final PsiElement prevSibling = lastElement.getPrevSibling();
      if (prevSibling == null || ".".equals(prevSibling.getText())) return false;
      PsiElement parent = prevSibling;
      do {
        parent = parent.getParent();
      } while(parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement);
      if (parent instanceof PsiParameterList) return false;

      if (!".".equals(lastElement.getText()) && !"#".equals(lastElement.getText())) {
        return false;
      }
      else{
        final PsiElement element = file.findElementAt(offset);
        if(element != null && "#".equals(lastElement.getText())
          && !new SuperParentFilter(new ClassFilter(PsiDocComment.class)).isAcceptable(element, element.getParent())){
          return false;
        }
        return true;
      }
    }
  }
}

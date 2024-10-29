// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class ProblematicWhitespaceInspection extends LocalInspectionTool {

  private static final class ShowWhitespaceFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return LangBundle.message("problematic.whitespace.show.whitespaces.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      final Editor editor = editorManager.getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final EditorSettings settings = editor.getSettings();
      settings.setLeadingWhitespaceShown(true);
      settings.setWhitespacesShown(!settings.isWhitespacesShown());
      editor.getComponent().repaint();
    }
  }

  private static final class ReformatFileFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return LangBundle.message("problematic.whitespace.reformat.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement file = descriptor.getPsiElement();
      if (!(file instanceof PsiFile)) {
        return;
      }
      CodeStyleManager.getInstance(project).reformat(file, true);
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ProblematicWhitespaceVisitor(holder, isOnTheFly);
  }

  private final class ProblematicWhitespaceVisitor extends PsiElementVisitor {

    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    ProblematicWhitespaceVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      super.visitFile(file);
      final FileType fileType = file.getFileType();
      if (!(fileType instanceof LanguageFileType)) {
        return;
      }
      if (file.getViewProvider().getBaseLanguage() != file.getLanguage()) {
        // don't warn multiple times on files which have multiple views like PHP and JSP
        return;
      }
      final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
      if (injectedLanguageManager.isInjectedFragment(file)) {
        return;
      }
      final CodeStyleSettings settings = CodeStyle.getSettings(file);
      final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptionsByFile(file);
      final boolean useTabs = indentOptions.USE_TAB_CHARACTER;
      final boolean smartTabs = indentOptions.SMART_TABS;
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return;
      }
      final int lineCount = document.getLineCount();
      int previousLineIndent = 0;
      for (int i = 0; i < lineCount; i++) {
        final int startOffset = document.getLineStartOffset(i);
        final int endOffset = document.getLineEndOffset(i);
        final String line = document.getText(new TextRange(startOffset, endOffset));
        boolean spaceSeen = false;
        for (int j = 0, length = line.length(); j < length; j++) {
          final char c = line.charAt(j);
          if (c == '\t') {
            if (useTabs) {
              if (smartTabs && spaceSeen) {
                if (registerError(file, startOffset, true)) {
                  return;
                }
              }
            }
            else {
              if (registerError(file, startOffset, false)) {
                return;
              }
            }
          }
          else if (c == ' ') {
            if (useTabs) {
              if (!smartTabs) {
                if (registerError(file, startOffset, true)) {
                  return;
                }
              }
              else if (!spaceSeen) {
                if (j < previousLineIndent) {
                  if (registerError(file, startOffset, true)) {
                    return;
                  }
                }
                previousLineIndent = j;
              }
            }
            spaceSeen = true;
          }
          else {
            if (!spaceSeen) {
              previousLineIndent = j;
            }
            break;
          }
        }
      }
    }

    private boolean registerError(PsiFile file, int startOffset, boolean tab) {
      final PsiElement element = file.findElementAt(startOffset);
      if (element != null && isSuppressedFor(element)) {
        return false;
      }
      final String description = tab
                                 ? LangBundle.message("problematic.whitespace.spaces.problem.descriptor", file.getName())
                                 : LangBundle.message("problematic.whitespace.tabs.problem.descriptor", file.getName());
      if (myIsOnTheFly) {
        myHolder.registerProblem(file, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new ReformatFileFix(), new ShowWhitespaceFix());
      }
      else {
        myHolder.registerProblem(file, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReformatFileFix());
      }
      return true;
    }
  }
}

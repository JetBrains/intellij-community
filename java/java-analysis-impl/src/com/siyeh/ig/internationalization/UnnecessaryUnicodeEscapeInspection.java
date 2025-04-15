// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryUnicodeEscapeInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final Character c = (Character)infos[0];
    if (c == '\n') {
      return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.newline.descriptor");
    }
    return InspectionGadgetsBundle.message("unnecessary.unicode.escape.problem.descriptor", (c == '\t') ? "\\t" : c);
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryUnicodeEscapeFix(((Character) infos[0]).charValue(), (RangeMarker)infos[1]);
  }

  private static class UnnecessaryUnicodeEscapeFix extends PsiUpdateModCommandQuickFix {

    private final char c;
    private final RangeMarker myRangeMarker;

    UnnecessaryUnicodeEscapeFix(char c, RangeMarker rangeMarker) {
      this.c = c;
      myRangeMarker = rangeMarker;
    }

    @Override
    public @NotNull String getName() {
      return switch (c) {
        case '\n' -> InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.text", 1);
        case '\t' -> InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.text", 2);
        case ' ' -> InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.text", 3);
        default -> CommonQuickFixBundle.message("fix.replace.with.x", c);
      };
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.unicode.escape.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      Document document = startElement.getContainingFile().getFileDocument();
      String replacement = c == '\t' && PsiUtil.isJavaToken(startElement, ElementType.STRING_LITERALS) ? "\\t" : String.valueOf(c);
      document.replaceString(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset(), replacement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnicodeEscapeVisitor();
  }

  private static class UnnecessaryUnicodeEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(@NotNull PsiFile file) {
      super.visitFile(file);
      if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file) || !file.isPhysical()) {
        return;
      }
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return;
      }
      final VirtualFile virtualFile = file.getVirtualFile();
      final String text = file.getText();
      final Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);
      final CharsetEncoder encoder = charset.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT);
      final CharBuffer charBuffer = CharBuffer.allocate(1);
      final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
      final int length = text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        if (c != '\\') {
          continue;
        }
        boolean isEscape = true;
        int previousChar = i - 1;
        while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
          isEscape = !isEscape;
          previousChar--;
        }
        if (!isEscape) {
          continue;
        }
        final int nextChar = detectUnicodeEscape(text, i, length);
        if (nextChar != -1) {
          final int escapeEnd = nextChar + 4;
          final char d = (char)Integer.parseInt(text.substring(nextChar, escapeEnd), 16);
          if (d == '\uFFFD' || (d == '\\' && detectUnicodeEscape(text, escapeEnd - 1, length) != -1)) {
            // this character is used as a replacement when a Unicode character can't be displayed
            // replacing the escape with the character may cause confusion, so ignore it.
            // skip if another escape sequence follows '\' without being properly escaped.
            continue;
          }
          final int type = Character.getType(d);
          if (type == Character.CONTROL && d != '\n' && d != '\t') {
            continue;
          }
          else if (type == Character.SPACE_SEPARATOR && d != ' ') {
            continue;
          }
          else if (type == Character.FORMAT
                   || type == Character.PRIVATE_USE
                   || type == Character.SURROGATE
                   || type == Character.UNASSIGNED
                   || type == Character.LINE_SEPARATOR
                   || type == Character.PARAGRAPH_SEPARATOR) {
            continue;
          }
          Character.UnicodeBlock block = Character.UnicodeBlock.of(d);
          if (block == Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS
              || block == Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS_EXTENDED
              || block == Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS_SUPPLEMENT
              || block == Character.UnicodeBlock.COMBINING_HALF_MARKS
              || block == Character.UnicodeBlock.COMBINING_MARKS_FOR_SYMBOLS) {
            continue;
          }
          byteBuffer.clear();
          charBuffer.clear();
          charBuffer.put(d).rewind();
          final CoderResult coderResult = encoder.encode(charBuffer, byteBuffer, true);
          if (coderResult.isError()) {
            continue;
          }
          PsiElement element = file.findElementAt(i);
          if (element == null) {
            return;
          }
          final RangeMarker rangeMarker = document.createRangeMarker(i, escapeEnd);
          TextRange range = element.getTextRange();
          while (escapeEnd > range.getEndOffset()) {
            element = element.getParent();
            range = element.getTextRange();
          }
          registerErrorAtOffset(element, i - range.getStartOffset(), escapeEnd - i, Character.valueOf(d), rangeMarker);
        }
      }
    }

    private static int detectUnicodeEscape(String text, int offset, int length) {
      int nextChar = offset + 1;
      while (nextChar < length && text.charAt(nextChar) == 'u') {
        nextChar++; // \uuuuFFFD is a legal Unicode escape
      }
      if (nextChar == offset + 1 || nextChar + 3 >= length) {
        return -1;
      }
      if (StringUtil.isHexDigit(text.charAt(nextChar)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 1)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 2)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 3))) {
        return nextChar;
      }
      return -1;
    }
  }
}

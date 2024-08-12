// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.folding;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.lang.surroundWith.ModCommandSurrounder;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static com.intellij.psi.util.PsiTreeUtil.skipParentsOfType;

public final class CustomFoldingSurroundDescriptor implements SurroundDescriptor {

  public static final CustomFoldingSurroundDescriptor INSTANCE = new CustomFoldingSurroundDescriptor();

  private static final String DEFAULT_DESC_TEXT = "Description";

  @Override
  public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    if (startOffset >= endOffset) return PsiElement.EMPTY_ARRAY;
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(file.getLanguage());
    if (commenter == null || commenter.getLineCommentPrefix() == null && 
                             (commenter.getBlockCommentPrefix() == null || commenter.getBlockCommentSuffix() == null)) {
      return PsiElement.EMPTY_ARRAY;
    }
    PsiElement startElement = file.findElementAt(startOffset);
    PsiElement endElement = file.findElementAt(endOffset - 1);
    if (startElement instanceof PsiWhiteSpace) {
      if (startElement == endElement) return PsiElement.EMPTY_ARRAY;
      startElement = startElement.getNextSibling();
    }
    if (endElement instanceof PsiWhiteSpace) endElement = endElement.getPrevSibling();
    if (startElement != null && endElement != null) {
      startElement = findClosestParentAfterLineBreak(startElement);
      if (startElement != null) {
        endElement = findClosestParentBeforeLineBreak(endElement);
        if (endElement != null) {
          return adjustRange(startElement, endElement);
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private static PsiElement @NotNull [] adjustRange(@NotNull PsiElement start, @NotNull PsiElement end) {
    PsiElement newStart = lowerStartElementIfNeeded(start, end);
    PsiElement newEnd = lowerEndElementIfNeeded(start, end);
    if (newStart == null || newEnd == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    PsiElement commonParent = findCommonAncestorForWholeRange(newStart, newEnd);
    if (commonParent != null) {
      return new PsiElement[] {commonParent};
    }
    // If either start or end element is the first/last leaf element in its parent, use the parent itself instead
    // to prevent selection of clearly illegal ranges like the following:
    // [
    //   <selection>1
    // ]</selection>
    // E.g. in case shown, because of that adjustment, closing bracket and number literal won't have the same parent
    // and the next test will fail.
    PsiElement newStartParent = getParent(newStart);
    if (newStartParent != null && newStartParent.getFirstChild() == newStart && newStart.getFirstChild() == null) {
      newStart = newStartParent;
    }
    PsiElement newEndParent = getParent(newEnd);
    if (newEndParent != null && newEndParent.getLastChild() == newEnd && newEnd.getFirstChild() == null) {
      newEnd = newEndParent;
    }
    if (getParent(newStart) == getParent(newEnd)) {
      return new PsiElement[] {newStart, newEnd};
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private static @Nullable PsiElement getParent(@Nullable PsiElement e) {
    return e instanceof PsiFile ? e : skipParentsOfType(e, GeneratedParserUtilBase.DummyBlock.class);
  }

  private static @Nullable PsiElement lowerEndElementIfNeeded(@NotNull PsiElement start, @NotNull PsiElement end) {
    if (PsiTreeUtil.isAncestor(end, start, true)) {
      PsiElement o = end.getLastChild();
      while (o != null && o.getParent() != start.getParent()) {
        PsiElement last = o.getLastChild();
        if (last == null) return o;
        o = last;
      }
      return o;
    }
    return end;
  }

  private static @Nullable PsiElement lowerStartElementIfNeeded(@NotNull PsiElement start, @NotNull PsiElement end) {
    if (PsiTreeUtil.isAncestor(start, end, true)) {
      PsiElement o = start.getFirstChild();
      while (o != null && o.getParent() != end.getParent()) {
        PsiElement first = o.getFirstChild();
        if (first == null) return o;
        o = first;
      }
      return o;
    }
    return start;
  }

  private static @Nullable PsiElement findCommonAncestorForWholeRange(@NotNull PsiElement start, @NotNull PsiElement end) {
    if (start.getContainingFile() != end.getContainingFile()) {
      return null;
    }
    final PsiElement parent = PsiTreeUtil.findCommonParent(start, end);
    if (parent == null) {
      return null;
    }
    final TextRange parentRange = parent.getTextRange();
    if (parentRange.getStartOffset() == start.getTextRange().getStartOffset() &&
        parentRange.getEndOffset() == end.getTextRange().getEndOffset()) {
      return parent;
    }
    return null;
  }

  private static @Nullable PsiElement findClosestParentAfterLineBreak(PsiElement element) {
    PsiElement parent = element;
    while (parent != null && !(parent instanceof PsiFileSystemItem)) {
      PsiElement prev = parent.getPrevSibling();
      while (prev != null && prev.getTextLength() <= 0) {
        prev = prev.getPrevSibling();
      }
      if (firstElementInFile(parent)) {
        return parent.getContainingFile();
      }
      else if (isWhiteSpaceWithLineFeed(prev)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private static boolean firstElementInFile(@NotNull PsiElement element) {
    return element.getTextOffset() == 0;
  }

  private static @Nullable PsiElement findClosestParentBeforeLineBreak(PsiElement element) {
    PsiElement parent = element;
    while (parent != null && !(parent instanceof PsiFileSystemItem)) {
      final PsiElement next = parent.getNextSibling();
      if (lastElementInFile(parent)) {
        return parent.getContainingFile();
      }
      else if (isWhiteSpaceWithLineFeed(next)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private static boolean lastElementInFile(@NotNull PsiElement element) {
    return element.getTextRange().getEndOffset() == element.getContainingFile().getTextRange().getEndOffset();
  }

  private static boolean isWhiteSpaceWithLineFeed(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    if (element instanceof PsiWhiteSpace) {
      return element.textContains('\n');
    }
    final ASTNode node = element.getNode();
    if (node == null) {
      return false;
    }
    final CharSequence text = node.getChars();
    boolean lineFeedFound = false;
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (!StringUtil.isWhiteSpace(c)) {
        return false;
      }
      lineFeedFound |= c == '\n';
    }
    return lineFeedFound;
  }

  @Override
  public Surrounder @NotNull [] getSurrounders() {
    //noinspection TestOnlyProblems
    return getAllSurrounders().toArray(new CustomFoldingRegionSurrounder[0]);
  }

  @TestOnly
  public static @NotNull List<Surrounder> getAllSurrounders() {
    return ContainerUtil.map(
      CustomFoldingProvider.getAllProviders(), provider -> new CustomFoldingRegionSurrounder(provider));
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  private static final class CustomFoldingRegionSurrounder extends ModCommandSurrounder {

    private final CustomFoldingProvider myProvider;

    CustomFoldingRegionSurrounder(@NotNull CustomFoldingProvider provider) {
      myProvider = provider;
    }

    @Override
    public String getTemplateDescription() {
      return myProvider.getDescription();
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement @NotNull [] elements) {
      if (elements.length == 0) return false;
      if (elements[0].getContainingFile() instanceof PsiCodeFragment) {
        return false;
      }
      Language language = elements[0].getLanguage();
      if (!myProvider.isSupported(language)) return false;
      for (FoldingBuilder each : LanguageFolding.INSTANCE.allForLanguage(language)) {
        if (myProvider.isSupportedBy(each)) return true;
      }
      return false;
    }

    @Override
    public @NotNull ModCommand surroundElements(@NotNull ActionContext context, @NotNull PsiElement @NotNull [] elements) {
      return ModCommand.psiUpdate(context, updater -> doSurround(context, ContainerUtil.map(elements, updater::getWritable), updater));
    }

    private void doSurround(@NotNull ActionContext context, @NotNull List<@NotNull PsiElement> elements, @NotNull ModPsiUpdater updater) {
      if (elements.isEmpty()) return;
      PsiElement firstElement = elements.get(0);
      PsiElement lastElement = elements.get(elements.size() - 1);
      PsiFile psiFile = firstElement.getContainingFile();
      String linePrefix;
      String lineSuffix;
      Language language = psiFile.getLanguage();
      if (myProvider.wrapStartEndMarkerTextInLanguageSpecificComment()) {
        Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
        if (commenter == null) return;
        linePrefix = commenter.getLineCommentPrefix();
        lineSuffix = "";
        if (linePrefix == null) {
          linePrefix = commenter.getBlockCommentPrefix();
          lineSuffix = StringUtil.notNullize(commenter.getBlockCommentSuffix());
        }
        if (linePrefix == null) return;
      }
      else {
        linePrefix = "";
        lineSuffix = "";
      }
      int prefixLength = linePrefix.length();

      int startOffset = firstElement.getTextRange().getStartOffset();
      final Document document = firstElement.getContainingFile().getFileDocument();
      final int startLineNumber = document.getLineNumber(startOffset);
      final String startIndent = document.getText(new TextRange(document.getLineStartOffset(startLineNumber), startOffset));
      int endOffset = lastElement.getTextRange().getEndOffset();
      int delta = 0;
      TextRange rangeToSelect = TextRange.create(startOffset, startOffset);
      String startText = myProvider.getStartString();
      int descPos = startText.indexOf("?");
      if (descPos >= 0) {
        startText = startText.replace("?", DEFAULT_DESC_TEXT);
        rangeToSelect = TextRange.from(startOffset + descPos, DEFAULT_DESC_TEXT.length());
      }

      String startString = linePrefix + startText + lineSuffix + "\n" + startIndent;
      String endString = "\n" + startIndent + linePrefix + myProvider.getEndString() + lineSuffix;
      document.insertString(endOffset, endString);
      delta += endString.length();
      document.insertString(startOffset, startString);
      delta += startString.length();
      
      RangeMarker rangeMarkerToSelect = document.createRangeMarker(rangeToSelect.shiftRight(prefixLength));
      Project project = context.project();
      PsiDocumentManager.getInstance(project).commitDocument(document);
      adjustLineIndent(project, psiFile, language, TextRange.from(endOffset + delta - endString.length(), endString.length()));
      adjustLineIndent(project, psiFile, language, TextRange.from(startOffset, startString.length()));
      rangeToSelect = TextRange.create(rangeMarkerToSelect.getStartOffset(), rangeMarkerToSelect.getEndOffset());
      rangeMarkerToSelect.dispose();
      updater.select(rangeToSelect);
    }

    private static void adjustLineIndent(@NotNull Project project, PsiFile file, Language language, TextRange range) {
      CodeStyleSettings settings = CodeStyle.getSettings(file);
      CodeStyleSettings cloneSettings = CodeStyleSettingsManager.getInstance(project).cloneSettings(settings);
      CommonCodeStyleSettings formatSettings = cloneSettings.getCommonSettings(language);
      formatSettings.KEEP_FIRST_COLUMN_COMMENT = false;
      CodeStyle.runWithLocalSettings(project, cloneSettings,
                                     () -> CodeStyleManager.getInstance(project).adjustLineIndent(file, range));
    }
  }
}

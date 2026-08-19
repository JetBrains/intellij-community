// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.migration.MarkdownDocumentationCommentsMigrationInspection;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.NullMarked;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.BLOCKQUOTE_PRE_PREFIX;
import static com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.BLOCKQUOTE_PRE_SUFFIX;
import static com.siyeh.ig.migration.MarkdownDocumentationCommentsMigrationInspection.MarkdownDocumentationCommentsMigrationFix.convertAndPostProcess;

/// The Markdown variant of the printer.
/// Since the intended use of this is the LSP clients, the [#printLinkURI(StringBuilder, PsiElement)] method is left abstract.
@NullMarked
public abstract class JavaDocInfoMarkdownPrinter implements JavaDocInfoPrinter {
  /// Detect relative links for patching
  private static final Pattern RELATIVE_LINK_PATTERN = Pattern.compile("\\[(.+?)]\\((.+?\\.html?.*?)\\)");
  private boolean myInCodeBlock;
  private boolean noBreakAfterSectionHeader;

  @Override
  public PsiDocComment preProcess(PsiDocComment docComment) {
    if (!docComment.isMarkdownComment()) {
      String convertedFileContent =
        convertAndPostProcess(docComment, new MarkdownDocumentationCommentsMigrationInspection.Settings(true));

      // Having only the tag in a Markdown comment does not truly deprecate the element
      if (ContainerUtil.exists(docComment.getTags(), tag -> tag.getName().equals("deprecated"))) {
        convertedFileContent += "\n @java.lang.Deprecated ";
      }

      return JavaPsiFacade.getElementFactory(docComment.getProject()).createDocCommentFromText(convertedFileContent, docComment);
    }
    return docComment;
  }

  @Override
  public @Nls @Nullable String postProcess(StringBuilder generatedDocument, Project project, @Nullable PsiElement context) {
    // A bit of a crutch, but fixing JavaDocInfoGenerator would take more effort than it may be worth rn
    @NlsSafe String postProcessedDoc = generatedDocument.toString()
      // some "pre" end tags are not properly removed by the JavadocInfoGenerator
      .replaceAll("```\\s*</pre>", "```")
      // some code blocks are handled differently in the main pass, making it hard to properly wrap them
      .replace(BLOCKQUOTE_PRE_PREFIX, "\n\n```\n")
      .replace(BLOCKQUOTE_PRE_SUFFIX, "\n```\n")
      .trim();

    // Patch relative links to real links
    if (context != null) {
      postProcessedDoc = RELATIVE_LINK_PATTERN.matcher(postProcessedDoc).replaceAll(result -> {
        Pair<PsiElement, @Nullable String> pair = JavaDocInfoGenerator.getElementForRelativeLink(result.group(2), context);
        if (pair == null) return result.group(0);

        return "[" + result.group(1) + "](" + printLinkURI(new StringBuilder(), pair.first) + ")";
      });
    }

    return postProcessedDoc;
  }

  @Override
  public StringBuilder printHighlightedText(StringBuilder builder,
                                            boolean doHighlighting,
                                            Project project,
                                            Language language,
                                            @Nullable String codeSnippet,
                                            float highlightSaturation) {
    if (codeSnippet != null) {
      builder.append(StringsKt.trimIndent(codeSnippet));
    }
    return builder;
  }

  @Override
  public StringBuilder printStylizedText(StringBuilder builder,
                                         boolean doHighlighting,
                                         TextAttributes attributes,
                                         @Nullable String value,
                                         float highlightSaturation) {
    if (value != null) {
      builder.append(value);
    }
    return builder;
  }

  @Override
  public StringBuilder printLink(StringBuilder builder,
                                 PsiElement targetElement,
                                 @Nullable String label,
                                 boolean plainLink) {
    StringBuilder target = new StringBuilder();
    printLinkURI(target, targetElement);
    if (!target.isEmpty()) {
      printLink(builder, target.toString(), label, plainLink);
    }
    return builder;
  }

  @Override
  public StringBuilder printLink(StringBuilder builder,
                                 String target,
                                 @Nullable String label,
                                 boolean plainLink) {
    if (myInCodeBlock) {
      return builder.append(label == null ? "" : label);
    }

    // Markdown doesn't support nested links. (Happens with things like "Type<E extends OtherType>") 
    // However, the label may already contain a Markdown link
    // The outer link is considered the winning one
    if (label != null) {
      label = label.replaceAll("\\[`?(.*?)`?]\\(.*?\\)", "$1");
    }
    
    builder.append('[');
    if (!plainLink) {
      appendCodeSpan(builder, label == null ? "" : label);
    }
    else if (label != null) {
      builder.append(label);
    }
    builder.append("](").append(target).append(')');
    return builder;
  }


  @Override
  public StringBuilder printUnresolvedLink(StringBuilder builder, String label) {
    return builder.append(label);
  }

  @Override
  public StringBuilder printInlineCode(StringBuilder builder,
                                       Project project,
                                       @Nullable Language language,
                                       String codeSnippet) {
    if (myInCodeBlock) {
      return builder.append(codeSnippet);
    }
    if (isInPre(builder)) {
      return builder.append("<code>").append(StringUtil.escapeXmlEntities(codeSnippet)).append("</code>");
    }

    return appendCodeSpan(builder, codeSnippet);
  }

  @Override
  public StringBuilder printCodeBlock(StringBuilder builder,
                                      Project project,
                                      @Nullable Language language,
                                      String codeSnippet) {
    if (myInCodeBlock) {
      return builder.append(codeSnippet);
    }

    String fence = getFence(codeSnippet, 3);
    appendLineBreak(builder);
    appendFenceStart(builder, fence, language);
    builder.append(StringUtil.trimLeadingLines(codeSnippet).stripTrailing());
    return appendFenceEnd(builder, fence);
  }

  @Override
  public StringBuilder printCodeBlockStart(StringBuilder builder, @Nullable String id, Language language, CodeBlockType type) {
    myInCodeBlock = true;
    appendLineBreak(builder);
    appendFenceStart(builder, "```", language);
    return builder;
  }

  @Override
  public StringBuilder printCodeBlockEnd(StringBuilder builder, CodeBlockType type) {
    StringBuilder result = appendFenceEnd(builder, "```");
    myInCodeBlock = false;
    return result;
  }

  @Override
  public StringBuilder printBoldText(StringBuilder builder, String value) {
    return myInCodeBlock ? builder.append(value) : builder.append("**").append(value).append("**");
  }

  @Override
  public StringBuilder printItalicText(StringBuilder builder, String value) {
    return myInCodeBlock ? builder.append(value) : builder.append('_').append(value).append('_');
  }

  @Override
  public StringBuilder printAnnotationHint(StringBuilder builder, boolean isInferred) {
    return isInferred && ApplicationManager.getApplication().isInternal()
           ? builder.append("⁽").append(JavaBundle.message("javadoc.description.inferred.annotation.hint.markdown")).append("⁾")
           : builder;
  }

  @Override
  public StringBuilder printParameterName(StringBuilder builder, String presentableName) {
    return builder.append('`').append(presentableName).append('`');
  }

  @Override
  public StringBuilder printPrologue(StringBuilder builder, @Nullable URL baseUrl) {
    return builder;
  }

  @Override
  public String getEscapableChar(char character) {
    return String.valueOf(character);
  }

  @Override
  public String escapeIfNeeded(String input) {
    return input;
  }

  @Override
  public StringBuilder printParagraph(StringBuilder builder) {
    // hacky, but format specific: no paragraph break right after a tag section ?
    if (noBreakAfterSectionHeader) {
      noBreakAfterSectionHeader = false;
      return appendNewLine(builder, 1);
    }
    return appendNewLine(builder, 2);
  }

  @Override
  public StringBuilder printLineBreak(StringBuilder builder) {
    return appendLineBreak(builder);
  }

  @Override
  public StringBuilder printDefinitionStart(StringBuilder builder) {
    myInCodeBlock = true;
    appendLineBreak(builder);
    appendFenceStart(builder, "```", JavaLanguage.INSTANCE);
    return builder;
  }

  @Override
  public StringBuilder printDefinitionEnd(StringBuilder builder) {
    StringBuilder result = appendFenceEnd(builder, "```");
    myInCodeBlock = false;
    return result;
  }

  @Override
  public StringBuilder printContentStart(StringBuilder builder) {
    return appendNewLine(appendNewLine(builder, 2).append("---"), 2);
  }

  @Override
  public StringBuilder printContentEnd(StringBuilder builder) {
    return builder;
  }

  @Override
  public StringBuilder printSectionsStart(StringBuilder builder) {
    return builder;
  }

  @Override
  public StringBuilder printSectionsEnd(StringBuilder builder) {
    noBreakAfterSectionHeader = false;
    return builder;
  }

  @Override
  public StringBuilder printSectionHeaderStart(StringBuilder builder) {
    noBreakAfterSectionHeader = true;
    return appendNewLine(builder, 2).append("**");
  }

  @Override
  public StringBuilder printSectionSeparator(StringBuilder builder) {
    return builder.append("**\n");
  }

  @Override
  public StringBuilder printSectionEnd(StringBuilder builder) {
    return appendLineBreak(builder);
  }

  @Override
  public StringBuilder printGrayedStart(StringBuilder builder) {
    return builder;
  }

  @Override
  public StringBuilder printGrayedEnd(StringBuilder builder) {
    return builder;
  }

  @Override
  public StringBuilder printContainerInfo(StringBuilder builder,
                                           @Nullable PsiElement element,
                                           String ownerLink,
                                           boolean ownerLinkIsCode) {

    String ownerText = element instanceof PsiPackage || element instanceof PsiClass ? "javadoc.description.container.info.from.package" :
                       element instanceof PsiMember ? "javadoc.description.container.info.from.class" : null;
    if (ownerText != null) {
      ownerText = JavaBundle.message(ownerText);
    }
    builder.append(ownerText).append(" ");

    return ownerLinkIsCode ? appendCodeSpan(builder, ownerLink) : builder.append(ownerLink);
  }

  @Override
  public StringBuilder printPackageClassesStart(StringBuilder builder, @Nls String heading) {
    return appendNewLine(builder, 2).append("### ").append(heading).append('\n');
  }

  @Override
  public StringBuilder printPackageClass(StringBuilder builder, PsiClass psiClass, @NlsSafe String link) {
    return appendLineBreak(builder).append(link);
  }

  @Override
  public void flushSubBuffer(StringBuilder buffer, StringBuilder subBuffer, boolean flushAsMarkdown) {
    buffer.append(subBuffer);
    subBuffer.setLength(0);
  }

  private static StringBuilder appendCodeSpan(StringBuilder builder, String value) {
    String fence = getFence(value);
    boolean addPadding = !value.isBlank() && (value.startsWith(" ") || value.endsWith(" ") ||
                                              value.startsWith("`") || value.endsWith("`"));
    builder.append(fence);
    if (addPadding) builder.append(' ');
    builder.append(value);
    if (addPadding) builder.append(' ');
    return builder.append(fence);
  }

  private static String getFence(String value) {
    return getFence(value, 1);
  }

  private static String getFence(String value, int minimumLength) {
    int longestRun = 0;
    int currentRun = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '`') {
        currentRun++;
        longestRun = Math.max(longestRun, currentRun);
      }
      else {
        currentRun = 0;
      }
    }
    return "`".repeat(Math.max(minimumLength, longestRun + 1));
  }

  private StringBuilder appendFenceEnd(StringBuilder builder, String fence) {
    return appendLineBreak(builder).append(fence).append('\n');
  }

  private StringBuilder appendLineBreak(StringBuilder builder) {

    appendNewLine(builder, 1);
    if (!myInCodeBlock && !builder.isEmpty()) {
      int offset = 1;
      for (int i = 1; i < builder.length() && i <= 2; i++) {
        char at = builder.charAt(builder.length() - i - 1);
        if (at == ' ') {
          offset += 1;
        }
        else if (at == '\n') {
          // Two line breaks
          return builder;
        }
        else {
          break;
        }
      }

      //Those 2 spaces + a new line make a short break in Markdown. Functionally invisible in a document
      builder.replace(Math.max(0, builder.length() - offset), builder.length(), "  \n");
    }
    return builder;
  }

  /// Ensures the minimum number of new lines when printing new ones
  private static StringBuilder appendNewLine(StringBuilder builder, @Range(from = 0, to = Integer.MAX_VALUE) int targetAmount) {
    if (builder.isEmpty()) return builder;
    for (int i = builder.length() - 1; i > 0 && builder.length() - i <= targetAmount; i--) {
      if (builder.charAt(i) != '\n') {
        int currentAmount = builder.length() - i - 1;
        if (targetAmount - currentAmount > 0) {
          builder.repeat('\n', targetAmount - currentAmount);
          return builder;
        }
        break;
      }
    }

    return builder;
  }

  private static void appendFenceStart(StringBuilder builder, String fence, @Nullable Language language) {
    builder.append(fence);
    if (language != null && !language.getID().isEmpty()) {
      builder.append(language.getID().toLowerCase(Locale.ROOT));
    }
    builder.append('\n');
  }

  /// Returns `true` if the builder is currently inside a `<pre>` block.
  /// Useful to determine if a fallback to HTML style presentation is needed.
  private static boolean isInPre(StringBuilder builder) {
    return builder.lastIndexOf("<pre>") != -1 && builder.lastIndexOf("</pre>") < builder.lastIndexOf("<pre>");
  }
}
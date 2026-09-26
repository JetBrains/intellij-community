// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.codeInsight.javadoc.markdown.JavaDocMarkdownFlavourDescriptor;
import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.markdown.utils.MarkdownToHtmlConverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.awt.Color;
import java.net.URL;
import java.util.Collection;
import java.util.Locale;

import static com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.BLOCKQUOTE_PRE_PREFIX;
import static com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.BLOCKQUOTE_PRE_SUFFIX;

/// The HTML variant of the printer
@NullMarked
public class JavaDocInfoHtmlPrinter implements JavaDocInfoPrinter {
  private static final Logger LOG = Logger.getInstance(JavaDocInfoHtmlPrinter.class);

  private static final String HREF_ATTRIBUTE_NAME = "href";
  private static final String BR_TAG = "<br>";
  private static final MarkdownToHtmlConverter ourMarkdownConverter = new MarkdownToHtmlConverter(new JavaDocMarkdownFlavourDescriptor());

  @Override
  public @Nls @Nullable String postProcess(StringBuilder generatedDocument, Project project, @Nullable PsiElement context) {
    String processed = sanitizeHtml(generatedDocument, context, project);
    return processed == null ? null : PlatformDocumentationUtil.fixupText(processed);
  }

  @Override
  public StringBuilder printHighlightedText(StringBuilder builder,
                                   boolean doHighlighting,
                                   Project project,
                                   Language language,
                                   @Nullable String codeSnippet,
                                   float highlightSaturation) {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        builder, project, language, codeSnippet, highlightSaturation);
    }
    else if (codeSnippet != null) {
      codeSnippet = StringsKt.trimIndent(codeSnippet);
      codeSnippet = StringUtil.escapeXmlEntities(codeSnippet);
      codeSnippet = codeSnippet.replace("\n", BR_TAG);
      builder.append(codeSnippet);
    }
    return builder;
  }

  @Override
  public StringBuilder printStylizedText(StringBuilder builder,
                                boolean doHighlighting,
                                TextAttributes attributes,
                                @Nullable String value,
                                float highlightSaturation) {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(builder, attributes, value, highlightSaturation);
    }
    else {
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
    DocumentationManagerUtil.createHyperlink(builder, target, label, plainLink);
    return builder;
  }

  @Override
  public StringBuilder printLinkURI(StringBuilder builder, PsiElement targetElement) {
    String refText = JavaDocUtil.getReferenceText(targetElement.getProject(), targetElement);
    if (refText != null) {
      builder.append(refText);
    }
    return builder;
  }

  @Override
  public StringBuilder printUnresolvedLink(StringBuilder builder, String label) {
    return builder.append(getSpanForUnresolvedItem()).append(label).append("</span>");
  }

  @Override
  public StringBuilder printInlineCode(StringBuilder builder,
                                       Project project,
                                       @Nullable Language language,
                                       String codeSnippet) {
    return QuickDocHighlightingHelper.appendStyledInlineCode(builder, project, language, codeSnippet);
  }

  @Override
  public StringBuilder printCodeBlock(StringBuilder builder,
                                      Project project,
                                      @Nullable Language language,
                                      String codeSnippet) {
    return QuickDocHighlightingHelper.appendStyledCodeBlock(builder, project, language, codeSnippet);
  }

  @Override
  public StringBuilder printCodeBlockStart(StringBuilder builder, @Nullable String id, Language language, CodeBlockType type) {
    return builder.append(
      type == CodeBlockType.BLOCKQUOTE_PRE
      ? BLOCKQUOTE_PRE_PREFIX
      : (id == null ? "<pre><code>" : "<pre id=\"" + StringUtil.escapeXmlEntities(id) + "\"><code>"));
  }

  @Override
  public StringBuilder printCodeBlockEnd(StringBuilder builder, CodeBlockType type) {
    return builder.append(type == CodeBlockType.BLOCKQUOTE_PRE ? BLOCKQUOTE_PRE_SUFFIX : "</code></pre>");
  }

  @Override
  public StringBuilder printBoldText(StringBuilder builder, String value) {
    return builder.append("<b>").append(value).append("</b>");
  }

  @Override
  public StringBuilder printItalicText(StringBuilder builder, String value) {
    return builder.append("<i>").append(value).append("</i>");
  }

  @Override
  public StringBuilder printAnnotationHint(StringBuilder builder, boolean isInferred) {
    if (isInferred && ApplicationManager.getApplication().isInternal()) {
      HtmlChunk.tag("sup").child(HtmlChunk.tag("font").attr("size", 3)
                                   .attr("color", ColorUtil.toHex(JBColor.GRAY))
                                   .child(HtmlChunk.tag("i")
                                            .addRaw(JavaBundle.message("javadoc.description.inferred.annotation.hint"))))
        .appendTo(builder);
    }
    HelpManager helpManager = HelpManager.getInstance();
    if (helpManager instanceof HelpManagerImpl) {
      String id = isInferred ? "inferred.annotations" : "external.annotations";
      String helpUrl = ApplicationManager.getApplication().isUnitTestMode() ? id : HelpManagerImpl.getHelpUrl(id);
      if (helpUrl != null) {
        HtmlChunk.link(helpUrl, DocumentationMarkup.EXTERNAL_LINK_ICON).appendTo(builder);
      }
    }
    return builder;
  }

  @Override
  public StringBuilder printParameterName(StringBuilder builder, String presentableName) {
    return builder.append("<code>").append(presentableName).append("</code>");
  }

  @Override
  public StringBuilder printPrologue(StringBuilder builder, @Nullable URL baseUrl) {
    if (baseUrl != null) {
      builder.append("<html><head><base href=\"").append(baseUrl).append("\"></head><body>");
    }
    return builder;
  }

  @Override
  public String getEscapableChar(char character) {
    return JavaDocInfoPrinter.escapeChar(character);
  }

  @Override
  public String escapeIfNeeded(String input) {
    return StringUtil.escapeXmlEntities(input);
  }

  @Override
  public StringBuilder printParagraph(StringBuilder builder) {
    return builder.append("<p>");
  }

  @Override
  public StringBuilder printLineBreak(StringBuilder builder) {
    return builder.append(BR_TAG);
  }

  @Override
  public StringBuilder printDefinitionStart(StringBuilder builder) {
    return builder.append(DocumentationMarkup.DEFINITION_START);
  }

  @Override
  public StringBuilder printDefinitionEnd(StringBuilder builder) {
    return builder.append(DocumentationMarkup.DEFINITION_END);
  }

  @Override
  public StringBuilder printContentStart(StringBuilder builder) {
    return builder.append(DocumentationMarkup.CONTENT_START);
  }

  @Override
  public StringBuilder printContentEnd(StringBuilder builder) {
    return builder.append(DocumentationMarkup.CONTENT_END);
  }

  @Override
  public StringBuilder printSectionsStart(StringBuilder builder) {
    return builder.append(DocumentationMarkup.SECTIONS_START);
  }

  @Override
  public StringBuilder printSectionsEnd(StringBuilder builder) {
    return builder.append(DocumentationMarkup.SECTIONS_END);
  }

  @Override
  public StringBuilder printSectionHeaderStart(StringBuilder builder) {
    return builder.append(DocumentationMarkup.SECTION_HEADER_START);
  }

  @Override
  public StringBuilder printSectionSeparator(StringBuilder builder) {
    return builder.append(DocumentationMarkup.SECTION_SEPARATOR);
  }

  @Override
  public StringBuilder printSectionEnd(StringBuilder builder) {
    return builder.append(DocumentationMarkup.SECTION_END);
  }

  @Override
  public StringBuilder printGrayedStart(StringBuilder builder) {
    return builder.append(DocumentationMarkup.GRAYED_START);
  }

  @Override
  public StringBuilder printGrayedEnd(StringBuilder builder) {
    return builder.append(DocumentationMarkup.GRAYED_END);
  }

  @Override
  public StringBuilder printContainerInfo(StringBuilder builder,
                                           @Nullable PsiElement element,
                                           String ownerLink,
                                           boolean ownerLinkIsCode) {
    String ownerIcon = element instanceof PsiPackage || element instanceof PsiClass ? "AllIcons.Nodes.Package" :
                       element instanceof PsiMember ? "AllIcons.Nodes.Class" : null;
    if (ownerIcon == null) return builder;

    @NlsSafe String ownerText = ownerLinkIsCode ? "<code>" + ownerLink + "</code>" : ownerLink;
    DocumentationMarkup.BOTTOM_ELEMENT
      .children(
        HtmlChunk.tag("icon").attr("src", ownerIcon),
        HtmlChunk.nbsp(),
        HtmlChunk.raw(ownerText)
      )
      .appendTo(builder);
    return builder;
  }

  @Override
  public StringBuilder printPackageClassesStart(StringBuilder builder, @Nls String heading) {
    HtmlChunk.tag("h3").addText(heading).appendTo(builder);
    return builder;
  }

  @Override
  public StringBuilder printPackageClass(StringBuilder builder, PsiClass psiClass, @NlsSafe String link) {
    HtmlChunk.tag("div")
      .children(
        HtmlChunk.tag("icon").attr("src", getIcon(psiClass)),
        HtmlChunk.nbsp(),
        HtmlChunk.raw(link)
      )
      .appendTo(builder);
    return builder;
  }

  @Override
  public void flushSubBuffer(StringBuilder buffer, StringBuilder subBuffer, boolean flushAsMarkdown) {
    buffer.append(flushAsMarkdown ? ourMarkdownConverter.convertMarkdownToHtml(subBuffer.toString().stripIndent(), null) : subBuffer);
    subBuffer.setLength(0);
  }

  private static String getSpanForUnresolvedItem() {
    TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
    Color color = attributes.getForegroundColor();
    String htmlColor = color == null ? "red" : ColorUtil.toHtmlColor(color);
    return "<span style=\"color:" + htmlColor + "\">";
  }

  private static String getIcon(PsiClass psiClass) {
    return psiClass.isEnum() ? "AllIcons.Nodes.Enum" :
           psiClass.isRecord() ? "AllIcons.Nodes.Record" :
           psiClass.isAnnotationType() ? "AllIcons.Nodes.Annotationtype" :
           psiClass.isInterface() ? "AllIcons.Nodes.Interface" :
           psiClass.hasModifierProperty(PsiModifier.ABSTRACT) ? "AllIcons.Nodes.AbstractClass" :
           "AllIcons.Nodes.Class";
  }

  private static @Nls @Nullable String sanitizeHtml(@Nls StringBuilder buffer, @Nullable PsiElement context, Project project) {
    String text = buffer.toString();
    if (text.isEmpty()) return null;
    if (context != null) {  // PSI element refs can't be resolved without a context
      StringBuilder result = new StringBuilder();
      int lastRef = 0;

      if (text.toUpperCase(Locale.ROOT).contains("HREF=\"")) {
        PsiFile fromText = PsiFileFactory.getInstance(project)
          .createFileFromText("DUMMY__.html", FileTypeManager.getInstance().getFileTypeByExtension("html"), text,
                              System.currentTimeMillis(), false);
        Collection<XmlTag> tags = PsiTreeUtil.findChildrenOfType(fromText, XmlTag.class);
        for (XmlTag tag : tags) {
          if (!tag.getName().toLowerCase(Locale.ROOT).equals("a")) {
            continue;
          }
          final XmlAttribute hrefAttribute = tag.getAttribute(HREF_ATTRIBUTE_NAME);
          if (hrefAttribute == null) {
            continue;
          }
          XmlAttributeValue hrefAttributeValueElement = hrefAttribute.getValueElement();
          if (hrefAttributeValueElement == null) {
            continue;
          }
          int groupStart = hrefAttributeValueElement.getValueTextRange().getStartOffset();
          int groupEnd = hrefAttributeValueElement.getValueTextRange().getEndOffset();
          result.append(text, lastRef, groupStart);
          String href = text.substring(groupStart, groupEnd);
          String reference = "";
          try {
            reference = JavaDocInfoGenerator.createReferenceForRelativeLink(href, context);
          }
          catch (IndexNotReadyException e) {
            LOG.debug(e);
            result.replace(result.length() - 6, result.length(), "wrong-href=\""); // display text instead of link
          }
          result.append(reference == null ? href : reference);
          lastRef = groupEnd;
        }
      }

      if (lastRef > 0) {  // don't copy text over if there are no matches
        result.append(text, lastRef, text.length());
        text = result.toString(); //NON-NLS
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    text = StringUtil.replaceIgnoreCase(text, "<p/>", "<p></p>"); //NON-NLS
    text = StringUtil.replace(text, "/>", ">");
    return text;
  }
}
/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexey
 */
public class FileHeaderChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.FileHeaderChecker");

  static ProblemDescriptor checkFileHeader(@NotNull final PsiFile file, @NotNull InspectionManager manager, boolean onTheFly) {
    TIntObjectHashMap<String> offsetToProperty = new TIntObjectHashMap<String>();
    FileTemplate defaultTemplate = FileTemplateManager.getInstance().getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    Pattern pattern = getTemplatePattern(defaultTemplate, file.getProject(), offsetToProperty);
    Matcher matcher = pattern.matcher(file.getViewProvider().getContents());
    if (matcher.matches()) {
      final int startOffset = matcher.start(1);
      final int endOffset = matcher.end(1);
      final Ref<PsiDocComment> docComment = new Ref<PsiDocComment>();
      file.accept(new JavaRecursiveElementWalkingVisitor(){
        @Override public void visitElement(PsiElement element) {
          if (docComment.get() != null) return;
          TextRange range = element.getTextRange();
          if (!range.contains(startOffset) && !range.contains(endOffset)) return;
          super.visitElement(element);
        }
        @Override public void visitDocComment(PsiDocComment comment) {
          docComment.set(comment);
        }
      });
      PsiDocComment element = docComment.get();
      if (element == null) return null;
      LocalQuickFix[] quickFix = createQuickFix(matcher, offsetToProperty);
      final String description = InspectionsBundle.message("default.file.template.description");
      return manager.createProblemDescriptor(element, description, onTheFly, quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    return null;
  }

  public static Pattern getTemplatePattern(@NotNull FileTemplate template, @NotNull Project project, @NotNull TIntObjectHashMap<String> offsetToProperty) {
    String templateText = template.getText().trim();
    String regex = templateToRegex(templateText, offsetToProperty, project);
    regex = StringUtil.replace(regex, "with", "(?:with|by)");
    regex = ".*("+regex+").*";
    return Pattern.compile(regex, Pattern.DOTALL);
  }

  private static Properties computeProperties(final Matcher matcher, final TIntObjectHashMap<String> offsetToProperty) {
    Properties properties = new Properties(FileTemplateManager.getInstance().getDefaultProperties());
    int[] offsets = offsetToProperty.keys();
    Arrays.sort(offsets);

    for (int i = 0; i < offsets.length; i++) {
      final int offset = offsets[i];
      String propName = offsetToProperty.get(offset);
      int groupNum = i + 2; // first group is whole doc comment
      String propValue = matcher.group(groupNum);
      properties.put(propName, propValue);
    }
    return properties;
  }

  private static LocalQuickFix[] createQuickFix(final Matcher matcher,
                                                final TIntObjectHashMap<String> offsetToProperty) {
    final FileTemplate template = FileTemplateManager.getInstance().getPattern(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);

    final ReplaceWithFileTemplateFix replaceTemplateFix = new ReplaceWithFileTemplateFix() {
      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null || !element.isValid()) return;
        if (!CodeInsightUtil.preparePsiElementsForWrite(element)) return;
        String newText;
        try {
          newText = template.getText(computeProperties(matcher, offsetToProperty));
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }
        try {
          int offset = element.getTextRange().getStartOffset();
          PsiFile psiFile = element.getContainingFile();
          if (psiFile == null) return;
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
          Document document = documentManager.getDocument(psiFile);
          if (document == null) return;

          element.delete();
          documentManager.doPostponedOperationsAndUnblockDocument(document);
          documentManager.commitDocument(document);

          document.insertString(offset, newText);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        catch (IllegalStateException e) {
          LOG.error("Cannot create doc comment from text: '" + newText + "'", e);
        }
      }
    };
    final LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceTemplateFix);
    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceTemplateFix,editFileTemplateFix};
  }

  private static String templateToRegex(@NotNull String text, @NotNull TIntObjectHashMap<String> offsetToProperty, @NotNull Project project) {
    String regex = text;
    @NonNls Collection<String> properties = new ArrayList<String>((Collection)FileTemplateManager.getInstance().getDefaultProperties(project).keySet());
    properties.add("PACKAGE_NAME");

    regex = escapeRegexChars(regex);
    // first group is a whole file header
    int groupNumber = 1;
    for (String name : properties) {
      String escaped = escapeRegexChars("${"+name+"}");
      boolean first = true;
      for (int i = regex.indexOf(escaped); i!=-1 && i<regex.length(); i = regex.indexOf(escaped,i+1)) {
        String replacement = first ? "([^\\n]*)" : "\\" + groupNumber;
        final int delta = escaped.length() - replacement.length();
        int[] offs = offsetToProperty.keys();
        for (int off : offs) {
          if (off > i) {
            String prop = offsetToProperty.remove(off);
            offsetToProperty.put(off - delta, prop);
          }
        }
        offsetToProperty.put(i, name);
        regex = regex.substring(0,i) + replacement + regex.substring(i+escaped.length());
        if (first) {
          groupNumber++;
          first = false;
        }
      }
    }
    return regex;
  }

  private static String escapeRegexChars(String regex) {
    regex = StringUtil.replace(regex,"|", "\\|");
    regex = StringUtil.replace(regex,".", "\\.");
    regex = StringUtil.replace(regex,"*", "\\*");
    regex = StringUtil.replace(regex,"+", "\\+");
    regex = StringUtil.replace(regex,"?", "\\?");
    regex = StringUtil.replace(regex,"$", "\\$");
    regex = StringUtil.replace(regex,"(", "\\(");
    regex = StringUtil.replace(regex,")", "\\)");
    regex = StringUtil.replace(regex,"[", "\\[");
    regex = StringUtil.replace(regex,"]", "\\]");
    regex = StringUtil.replace(regex,"{", "\\{");
    regex = StringUtil.replace(regex,"}", "\\}");
    return regex;
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class FileHeaderChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.FileHeaderChecker");

  static ProblemDescriptor checkFileHeader(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean onTheFly) {
    TIntObjectHashMap<String> offsetToProperty = new TIntObjectHashMap<String>();
    FileTemplate defaultTemplate = FileTemplateManager.getInstance(file.getProject()).getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    Pattern pattern = getTemplatePattern(defaultTemplate, file.getProject(), offsetToProperty);
    Matcher matcher = pattern.matcher(file.getViewProvider().getContents());
    if (!matcher.matches()) {
      return null;
    }

    PsiComment element = PsiTreeUtil.findElementOfClassAtRange(file, matcher.start(1), matcher.end(1), PsiComment.class);
    if (element == null) {
      return null;
    }

    LocalQuickFix[] fixes = createQuickFix(matcher, offsetToProperty, file.getProject());
    String description = InspectionsBundle.message("default.file.template.description");
    return manager.createProblemDescriptor(element, description, onTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  public static Pattern getTemplatePattern(@NotNull FileTemplate template,
                                           @NotNull Project project,
                                           @NotNull TIntObjectHashMap<String> offsetToProperty) {
    String templateText = template.getText().trim();
    String regex = templateToRegex(templateText, offsetToProperty, project);
    regex = StringUtil.replace(regex, "with", "(?:with|by)");
    regex = ".*(" + regex + ").*";
    return Pattern.compile(regex, Pattern.DOTALL);
  }

  private static Properties computeProperties(final Matcher matcher, final TIntObjectHashMap<String> offsetToProperty, Project project) {
    Properties properties = new Properties(FileTemplateManager.getInstance(project).getDefaultProperties());

    int[] offsets = offsetToProperty.keys();
    Arrays.sort(offsets);
    for (int i = 0; i < offsets.length; i++) {
      final int offset = offsets[i];
      String propName = offsetToProperty.get(offset);
      int groupNum = i + 2; // first group is whole doc comment
      String propValue = matcher.group(groupNum);
      properties.setProperty(propName, propValue);
    }

    return properties;
  }

  private static LocalQuickFix[] createQuickFix(final Matcher matcher, final TIntObjectHashMap<String> offsetToProperty, Project project) {
    final FileTemplate template = FileTemplateManager.getInstance(project).getPattern(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);

    ReplaceWithFileTemplateFix replaceTemplateFix = new ReplaceWithFileTemplateFix() {
      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null || !element.isValid()) return;
        if (!CodeInsightUtil.preparePsiElementsForWrite(element)) return;

        String newText;
        try {
          newText = template.getText(computeProperties(matcher, offsetToProperty, project)).trim();
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }

        if (!newText.isEmpty()) {
          PsiComment newComment = JavaPsiFacade.getElementFactory(project).createCommentFromText(newText, null);
          element.replace(newComment);
        }
        else {
          element.delete();
        }
      }
    };

    LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceTemplateFix);
    return template.isDefault() ? new LocalQuickFix[]{editFileTemplateFix} : new LocalQuickFix[]{replaceTemplateFix, editFileTemplateFix};
  }

  private static String templateToRegex(String text, TIntObjectHashMap<String> offsetToProperty, Project project) {
    List<Object> properties = ContainerUtil.newArrayList(FileTemplateManager.getInstance(project).getDefaultProperties().keySet());
    properties.add("PACKAGE_NAME");

    String regex = escapeRegexChars(text);
    // first group is a whole file header
    int groupNumber = 1;
    for (Object property : properties) {
      String name = property.toString();
      String escaped = escapeRegexChars("${" + name + "}");
      boolean first = true;
      for (int i = regex.indexOf(escaped); i != -1 && i < regex.length(); i = regex.indexOf(escaped, i + 1)) {
        String replacement = first ? "([^\\n]*)" : "\\" + groupNumber;
        int delta = escaped.length() - replacement.length();
        int[] offs = offsetToProperty.keys();
        for (int off : offs) {
          if (off > i) {
            String prop = offsetToProperty.remove(off);
            offsetToProperty.put(off - delta, prop);
          }
        }
        offsetToProperty.put(i, name);
        regex = regex.substring(0, i) + replacement + regex.substring(i + escaped.length());
        if (first) {
          groupNumber++;
          first = false;
        }
      }
    }
    return regex;
  }

  private static String escapeRegexChars(String regex) {
    regex = StringUtil.replace(regex, "|", "\\|");
    regex = StringUtil.replace(regex, ".", "\\.");
    regex = StringUtil.replace(regex, "*", "\\*");
    regex = StringUtil.replace(regex, "+", "\\+");
    regex = StringUtil.replace(regex, "?", "\\?");
    regex = StringUtil.replace(regex, "$", "\\$");
    regex = StringUtil.replace(regex, "(", "\\(");
    regex = StringUtil.replace(regex, ")", "\\)");
    regex = StringUtil.replace(regex, "[", "\\[");
    regex = StringUtil.replace(regex, "]", "\\]");
    regex = StringUtil.replace(regex, "{", "\\{");
    regex = StringUtil.replace(regex, "}", "\\}");
    return regex;
  }
}

package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
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

  static ProblemDescriptor checkFileHeader(final PsiFile file,
                                           final InspectionManager manager) {
    FileTemplate template = FileTemplateManager.getInstance().getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    String text = template.getText();
    TIntObjectHashMap<String> offsetToProperty = new TIntObjectHashMap<String>();
    String regex = templateToRegex(text, offsetToProperty);
    regex = ".*("+regex+").*";
    String fileText = file.getText();
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(fileText);
    if (matcher.matches()) {
      final int startOffset = matcher.start(1);
      final int endOffset = matcher.end(1);
      final Ref<PsiDocComment> docComment = new Ref<PsiDocComment>();
      file.accept(new PsiRecursiveElementVisitor(){
        public void visitElement(PsiElement element) {
          if (docComment.get() != null) return;
          TextRange range = element.getTextRange();
          if (!range.contains(startOffset) && !range.contains(endOffset)) return;
          super.visitElement(element);
        }
        public void visitDocComment(PsiDocComment comment) {
          docComment.set(comment);
        }
      });
      PsiDocComment element = docComment.get();
      if (element == null) return null;
      LocalQuickFix[] quickFix = createQuickFix(element, matcher, offsetToProperty);
      final String description = InspectionsBundle.message("default.file.template.description");
      return manager.createProblemDescriptor(element, description, quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    return null;
  }

  private static LocalQuickFix[] createQuickFix(final PsiDocComment element,
                                              final Matcher matcher,
                                              final TIntObjectHashMap<String> offsetToProperty) {
    final FileTemplate template = FileTemplateManager.getInstance().getPattern(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    final ReplaceWithFileTemplateFix replaceTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
        String newText;
        try {
          newText = template.getText(computeProperties(matcher, offsetToProperty));
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }
        try {
          PsiDocComment newDoc = element.getManager().getElementFactory().createDocCommentFromText(newText, element);
          element.replace(newDoc);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        catch (IllegalStateException e) {
          LOG.error("Cannot create doc comment from text: '"+newText+"'",e);
        }
      }

      private Properties computeProperties(final Matcher matcher, final TIntObjectHashMap<String> offsetToProperty) {
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
    };
    final LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceTemplateFix);
    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceTemplateFix,editFileTemplateFix};
  }

  private static String templateToRegex(final String text, TIntObjectHashMap<String> offsetToProperty) {
    String regex = text;
    @NonNls Collection<String> properties = new ArrayList<String>((Collection)FileTemplateManager.getInstance().getDefaultProperties().keySet());
    properties.add("PACKAGE_NAME");

    regex = escapeRegexChars(regex);
    // first group is a whole file header
    int groupNumber = 1;
    for (String name : properties) {
      String escaped = escapeRegexChars("${"+name+"}");
      boolean first = true;
      for (int i = regex.indexOf(escaped); i!=-1 && i<regex.length(); i = regex.indexOf(escaped,i+1)) {
        String replacement = first ? "(.*)" : "\\" + groupNumber;
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

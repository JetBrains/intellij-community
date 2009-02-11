package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.CreatePropertyFix;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 06.02.2009
 * Time: 21:13:07
 * To change this template use File | Settings | File Templates.
 */
public class JavaCreatePropertyFix extends CreatePropertyFix {
  public JavaCreatePropertyFix() {}

  public JavaCreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    super(element, key, propertiesFiles);
  }

  @Nullable
  protected static Pair<String, String> invokeAction(@NotNull final Project project,
                                                     @NotNull PsiFile file,
                                                     @NotNull PsiElement psiElement,
                                                     @Nullable final String suggestedKey,
                                                     @Nullable String suggestedValue,
                                                     @Nullable final List<PropertiesFile> propertiesFiles) {
    final PsiLiteralExpression literalExpression = psiElement instanceof PsiLiteralExpression ? (PsiLiteralExpression)psiElement : null;
    final String propertyValue = suggestedValue == null ? "" : suggestedValue;

    final I18nizeQuickFixDialog dialog = new JavaI18nizeQuickFixDialog(
      project,
      file,
      literalExpression,
      propertyValue,
      createDefaultCustomization(suggestedKey, propertiesFiles),
      false,
      false
    );
    return doAction(project, psiElement, dialog);
  }

}

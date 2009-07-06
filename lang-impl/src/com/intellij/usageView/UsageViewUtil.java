package com.intellij.usageView;

import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 */
public class UsageViewUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageViewUtil");

  private UsageViewUtil() { }

  public static String createNodeText(PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE);
  }

  public static String getMetaDataName(final PsiMetaData metaData) {
    final String name = metaData.getName();
    return StringUtil.isEmpty(name) ? "''" : name;
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewShortNameLocation.INSTANCE);
  }

  public static String getLongName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewLongNameLocation.INSTANCE);
  }

  public static String getType(@NotNull PsiElement psiElement) {
    return ElementDescriptionUtil.getElementDescription(psiElement, UsageViewTypeLocation.INSTANCE);
  }

  public static String getDescriptiveName(@NotNull PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());

    if (psiElement instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)psiElement;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) return getMetaDataName(metaData);
    }

    final Language lang = psiElement.getLanguage();
    FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(lang);
    return provider.getDescriptiveName(psiElement);
  }

  public static boolean hasNonCodeUsages(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) return true;
    }
    return false;
  }

  public static boolean hasReadOnlyUsages(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (!usage.isWritable()) return true;
    }
    return false;
  }

  public static UsageInfo[] removeDuplicatedUsages(@NotNull UsageInfo[] usages) {
    Set<UsageInfo> set = new LinkedHashSet<UsageInfo>(Arrays.asList(usages));
    return set.toArray(new UsageInfo[set.size()]);
  }
}
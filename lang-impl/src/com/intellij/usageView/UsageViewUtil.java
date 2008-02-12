package com.intellij.usageView;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.ant.PsiAntElement;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.TypeNameManager;
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

  public static String createNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)element;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        return ((PsiPresentableMetaData)metaData).getTypeName() + " " + getMetaDataName(metaData);
      }
    }

    if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      final PsiMetaData metaData = xmlTag.getMetaData();
      final String name = metaData != null ? getMetaDataName(metaData) : xmlTag.getName();
      return UsageViewBundle.message("usage.target.xml.tag.of.file", metaData == null ? "<" + name + ">" : name, xmlTag.getContainingFile().getName());
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    else if (element != null) {
      FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage());
      return provider.getNodeText(element, useFullName);
    }

    return "";
  }

  public static String getMetaDataName(final PsiMetaData metaData) {
    final String name = metaData.getName();
    return StringUtil.isEmpty(name) ? "''" : name;
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    final String name = ElementDescriptionUtil.getElementDescription(psiElement, UsageViewShortNameLocation.INSTANCE);
    return name == null ? "" : name;
  }

  public static String getLongName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    final String desc = ElementDescriptionUtil.getElementDescription(psiElement, UsageViewLongNameLocation.INSTANCE);
    return desc == null ? "" : desc;
  }

  public static String getType(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)psiElement).getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        return ((PsiPresentableMetaData)metaData).getTypeName();
      }
    }
    if (psiElement instanceof XmlTag) {
      final PsiMetaData metaData = ((XmlTag)psiElement).getMetaData();
      if (metaData != null && metaData.getDeclaration() instanceof XmlTag) {
        return ((XmlTag)metaData.getDeclaration()).getName();
      }
      return LangBundle.message("xml.terms.xml.tag");
    }

    if (psiElement instanceof PsiAntElement) {
      return ((PsiAntElement)psiElement).getRole().getName();
    }
    if (psiElement instanceof PsiFile) {
      return LangBundle.message("terms.file");
    }
    if (psiElement instanceof PsiDirectory) {
      return LangBundle.message("terms.directory");
    }

    final Language lang = psiElement.getLanguage();
    FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(lang);
    final String type = provider.getType(psiElement);
    if (StringUtil.isNotEmpty(type)) {
      return type;
    }

    return TypeNameManager.getTypeName(psiElement.getClass());
  }

  public static String getDescriptiveName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());

    if (psiElement instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)psiElement;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) return getMetaDataName(metaData);
    }

    if (psiElement instanceof XmlTag) {
      return ((XmlTag)psiElement).getName();
    }

    if (psiElement instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)psiElement).getValue();
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
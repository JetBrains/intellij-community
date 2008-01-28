package com.intellij.usageView;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.ant.PsiAntElement;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PsiFormatUtil;
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

  private static String getMetaDataName(final PsiMetaData metaData) {
    final String name = metaData.getName();
    return StringUtil.isEmpty(name) ? "''" : name;
  }

  public static String getShortName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    if (psiElement instanceof PsiMetaOwner) {
      PsiMetaData metaData = ((PsiMetaOwner)psiElement).getMetaData();
      if (metaData!=null) return getMetaDataName(metaData);
    }

    String ret = "";
    if (psiElement instanceof PsiNamedElement) {
      ret = ((PsiNamedElement)psiElement).getName();
    }
    else if (psiElement instanceof PsiThrowStatement) {
      ret = UsageViewBundle.message("usage.target.exception");
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    return ret;
  }

  public static String getLongName(final PsiElement psiElement) {
    LOG.assertTrue(psiElement.isValid());
    String ret;
    if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)psiElement));
      if (aPackage != null) {
        ret = aPackage.getQualifiedName();
      }
      else {
        ret = ((PsiDirectory)psiElement).getVirtualFile().getPresentableUrl();
      }
    }
    else if (psiElement instanceof PsiPackage) {
      ret = ((PsiPackage)psiElement).getQualifiedName();
    }
    else if (psiElement instanceof PsiClass) {
      if (psiElement instanceof PsiAnonymousClass) {
        ret = LangBundle.message("java.terms.anonymous.class");
      }
      else {
        ret = ((PsiClass)psiElement).getQualifiedName(); // It happens for local classes
        if (ret == null) {
          ret = ((PsiClass)psiElement).getName();
        }
      }
    }
    else if (psiElement instanceof PsiVariable) {
      ret = ((PsiVariable)psiElement).getName();
    }
    else if (psiElement instanceof XmlTag) {
      ret = ((XmlTag)psiElement).getName();
    }
    else if (psiElement instanceof XmlAttributeValue) {
      ret = ((XmlAttributeValue)psiElement).getValue();
    }
    else if (psiElement instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)psiElement;
      ret = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    }
    else {
      ret = "";
    }
    return ret;
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
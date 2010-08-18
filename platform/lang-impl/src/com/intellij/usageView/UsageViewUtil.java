/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.usageView;

import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
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
    
    // Replace duplicates of move rename usage infos in injections from non code usages of master files
    String newTextInNonCodeUsage = null;
    
    for(UsageInfo usage:usages) {
      if (!(usage instanceof NonCodeUsageInfo)) continue;
      newTextInNonCodeUsage = ((NonCodeUsageInfo)usage).newText;
      break;
    }
    
    if (newTextInNonCodeUsage != null) {
      for(UsageInfo usage:usages) {
        if (!(usage instanceof MoveRenameUsageInfo)) continue;
        PsiFile file = usage.getFile();
        
        if (file != null) {
          PsiElement context = file.getContext();
          if (context != null) {
  
            PsiElement usageElement = usage.getElement();
            if (usageElement == null) continue;
  
            PsiReference psiReference = usage.getReference();
            if (psiReference == null) continue;
            
            int injectionOffsetInMasterFile = InjectedLanguageManager.getInstance(usageElement.getProject()).injectedToHost(usageElement, usageElement.getTextOffset());
            set.remove(
              NonCodeUsageInfo.create(
                context.getContainingFile(), 
                usage.startOffset + injectionOffsetInMasterFile, 
                usage.endOffset + injectionOffsetInMasterFile,
                ((MoveRenameUsageInfo)usage).getReferencedElement(), 
                newTextInNonCodeUsage
              )
            );
          }
        }
      }
    }
    return set.toArray(new UsageInfo[set.size()]);
  }
}
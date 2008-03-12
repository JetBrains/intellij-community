package com.intellij.ide.impl.dataRules;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
public class UsageTargetsRule implements GetDataRule {
  private final ExtensionPointName<UsageTargetProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageTargetProvider");

  @Nullable
  public Object getData(DataProvider dataProvider) {
    List<UsageTarget> result = new ArrayList<UsageTarget>();

    final UsageTargetProvider[] providers = Extensions.getRootArea().getExtensionPoint(EP_NAME).getExtensions();
    for (UsageTargetProvider provider : providers) {
      final UsageTarget[] targets = provider.getTargetsAtContext(dataProvider);
      if (targets != null) {
        result.addAll(Arrays.asList(targets));
      }
    }

    PsiElement psiElement = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (psiElement instanceof NavigationItem) {
      if (FindManager.getInstance(psiElement.getProject()).canFindUsages(psiElement)) {
        result.add(new PsiElement2UsageTargetAdapter(psiElement));
      }
    }

    return result.isEmpty() ? null : result.toArray(new UsageTarget[result.size()]);
  }
}

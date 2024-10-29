// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.findUsages;

import com.intellij.lang.HelpID;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommonFindUsagesDialog extends AbstractFindUsagesDialog {
  protected final @NotNull PsiElement myPsiElement;
  private final @Nullable String myHelpId;
  protected final @NotNull FindUsagesHandlerBase myUsagesHandler;

  public CommonFindUsagesDialog(@NotNull PsiElement element,
                                @NotNull Project project,
                                @NotNull FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab,
                                boolean mustOpenInNewTab,
                                boolean isSingleFile,
                                @NotNull FindUsagesHandlerBase handler) {
    super(project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, isTextSearch(element, isSingleFile, handler),
          true);
    myPsiElement = element;
    myUsagesHandler = handler;
    String helpId = handler instanceof FindUsagesHandlerUi?
       ((FindUsagesHandlerUi)handler).getHelpId(): null;
    myHelpId = ObjectUtils.chooseNotNull(helpId, HelpID.FIND_OTHER_USAGES);
    init();
  }

  private static boolean isTextSearch(@NotNull PsiElement element, boolean isSingleFile, @NotNull FindUsagesHandlerBase handler) {
    return FindUsagesUtil.isSearchForTextOccurrencesAvailable(element, isSingleFile, handler);
  }

  @Override
  protected boolean isInFileOnly() {
    if (super.isInFileOnly()) return true;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-347939, EA-976313")) {
      Project project = myPsiElement.getProject();
      SearchScope useScope = PsiSearchHelper.getInstance(project).getUseScope(myPsiElement);
      if (useScope instanceof LocalSearchScope) return true;
    }
    return false;
  }

  @Override
  public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
    coloredComponent.append(StringUtil.capitalize(UsageViewUtil.getType(myPsiElement)));
    coloredComponent.append(" ");
    ReadAction.nonBlocking(() -> DescriptiveNameUtil.getDescriptiveName(myPsiElement))
      .expireWith(getDisposable())
      .finishOnUiThread(ModalityState.any(), name -> coloredComponent.append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  protected @Nullable String getHelpId() {
    return myHelpId;
  }
}

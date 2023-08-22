// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.DaemonTooltipsUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.find.findUsages.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class UnusedSymbolUtil {

  public static boolean isInjected(@NotNull Project project, @NotNull PsiModifierListOwner modifierListOwner) {
    return EntryPointsManagerBase.getInstance(project).isEntryPoint(modifierListOwner);
  }

  public static boolean isImplicitUsage(@NotNull Project project, @NotNull PsiModifierListOwner element) {
    if (isInjected(project, element)) return true;
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isImplicitRead(@NotNull PsiVariable variable) {
    return isImplicitRead(variable.getProject(), variable);
  }

  public static boolean isImplicitRead(@NotNull Project project, @NotNull PsiVariable element) {
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return isInjected(project, element);
  }

  public static boolean isImplicitWrite(@NotNull PsiVariable variable) {
    return isImplicitWrite(variable.getProject(), variable);
  }

  public static boolean isImplicitWrite(@NotNull Project project, @NotNull PsiVariable element) {
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return EntryPointsManager.getInstance(project).isImplicitWrite(element);
  }

  /**
   * @deprecated use {@link #createUnusedSymbolInfoBuilder(PsiElement, String, HighlightInfoType, String)} instead
   */
  @Deprecated
  @Nullable
  public static HighlightInfo createUnusedSymbolInfo(@NotNull PsiElement element,
                                                     @NotNull @NlsContexts.DetailedDescription String message,
                                                     @NotNull final HighlightInfoType highlightInfoType,
                                                     @Nullable String shortName) {
    return createUnusedSymbolInfoBuilder(element, message, highlightInfoType, shortName).create();
  }
  @NotNull
  public static HighlightInfo.Builder createUnusedSymbolInfoBuilder(@NotNull PsiElement element,
                                                                    @NotNull @NlsContexts.DetailedDescription String message,
                                                                    @NotNull final HighlightInfoType highlightInfoType,
                                                                    @Nullable String shortName) {
    String tooltip;
    if (shortName != null) {
      tooltip = DaemonTooltipsUtil.getWrappedTooltip(message, shortName, true);
    }
    else {
      tooltip = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message));
    }

    HighlightInfo.@NotNull Builder info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element)
      .description(message).escapedToolTip(tooltip).group(
      GeneralHighlightingPass.POST_UPDATE_ALL);

    for (UnusedDeclarationFixProvider provider : UnusedDeclarationFixProvider.EP_NAME.getExtensionList()) {
      IntentionAction[] fixes = provider.getQuickFixes(element);
      for (IntentionAction fix : fixes) {
        info.registerFix(fix, null, null, null, null);
      }
    }
    return info;
  }

  public static boolean isFieldUnused(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiField field,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(field)) {
      return false;
    }
    if (field instanceof PsiEnumConstant && isEnumValuesMethodUsed(project, containingFile, field, progress, helper)) {
      return false;
    }
    return weAreSureThereAreNoUsages(project, containingFile, field, progress, helper);
  }

  public static boolean isMethodReferenced(@NotNull Project project,
                                           @NotNull PsiFile containingFile,
                                           @NotNull PsiMethod method,
                                           @NotNull ProgressIndicator progress,
                                           @NotNull GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(method)) return true;

    boolean isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    PsiClass containingClass = method.getContainingClass();
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return true;
    if (isPrivate) {
      if (isIntentionalPrivateConstructor(method, containingClass)) {
        return true;
      }
      if (isImplicitUsage(project, method)) {
        return true;
      }
      if (!helper.isCurrentFileAlreadyChecked()) {
        return !weAreSureThereAreNoUsages(project, containingFile, method, progress, helper);
      }
    }
    else {
      //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
      if (isTheOnlyConstructor(method, containingClass) &&
          isClassUsed(project, containingFile, containingClass, progress, helper)) {
        return true;
      }
      if (isImplicitUsage(project, method)) return true;

      if (!method.isConstructor() && FindSuperElementsHelper.findSuperElements(method).length != 0) {
        return true;
      }
      return !weAreSureThereAreNoUsages(project, containingFile, method, progress, helper);
    }
    return false;
  }

  private static boolean isTheOnlyConstructor(@NotNull PsiMethod method, @Nullable PsiClass containingClass) {
    return method.isConstructor() && containingClass != null && containingClass.getConstructors().length == 1;
  }

  private static boolean weAreSureThereAreNoUsages(@NotNull Project project,
                                                   @NotNull PsiFile containingFile,
                                                   @NotNull final PsiMember member,
                                                   @NotNull ProgressIndicator progress,
                                                   @NotNull GlobalUsageHelper helper) {
    log("* " + member.getName() + ": call wearesure");
    if (!helper.shouldCheckUsages(member)) {
      log("* "+member.getName()+": should not check");
      return false;
    }

    final PsiFile ignoreFile = helper.isCurrentFileAlreadyChecked() ? containingFile : null;

    boolean sure = processUsages(project, containingFile, member, progress, ignoreFile, info -> {
      PsiFile psiFile = info.getFile();
      if (psiFile == ignoreFile || psiFile == null) {
        return true; // ignore usages in containingFile because isLocallyUsed() method would have caught that
      }
      int offset = info.getNavigationOffset();
      if (offset == -1) return true;
      PsiElement element = psiFile.findElementAt(offset);
      boolean inComment = element instanceof PsiComment;
      log("*     "+member.getName()+": usage :"+element);
      return inComment; // ignore comments
    });
    log("*     "+member.getName()+": result:"+sure);
    return sure;
  }

  private static void log(@NonNls String s) {
    //System.out.println(s);
  }

  @NotNull
  public static SearchScope getUseScope(@NotNull PsiMember member) {
    Project project = member.getProject();
    SearchScope useScope = PsiSearchHelper.getInstance(project).getUseScope(member);
    // some classes may have references from within XML outside dependent modules, e.g. our actions
    if (useScope instanceof GlobalSearchScope globalUseScope && member instanceof PsiClass) {
      useScope = GlobalSearchScope.projectScope(project).uniteWith(globalUseScope);
    }
    return useScope;
  }

  // return false if can't process usages (weird member of too may usages) or processor returned false
  public static boolean processUsages(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiMember member,
                                      @NotNull ProgressIndicator progress,
                                      @Nullable PsiFile ignoreFile,
                                      @NotNull Processor<? super UsageInfo> usageInfoProcessor) {
    return processUsages(project, containingFile, getUseScope(member), member, progress, ignoreFile, usageInfoProcessor);
  }

  public static boolean processUsages(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull final SearchScope useScope,
                                      @NotNull PsiMember member,
                                      @NotNull ProgressIndicator progress,
                                      @Nullable PsiFile ignoreFile,
                                      @NotNull Processor<? super UsageInfo> usageInfoProcessor) {
    String name = member.getName();
    if (name == null) {
      log("* "+member.getName()+" no name; false");
      return false;
    }
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
    if (useScope instanceof GlobalSearchScope) {

      PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, ignoreFile, progress);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
          // try to search for private and package-private members unconditionally - they are unlikely to have millions of usages
          && (member.hasModifierProperty(PsiModifier.PUBLIC) || member.hasModifierProperty(PsiModifier.PROTECTED))) {
        log("* "+member.getName()+" too many usages; false");
        return false;
      }

      //search usages if it cheap
      //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES && !canBeReferencedViaWeirdNames(member, containingFile)) {
        log("* "+member.getName()+" 0 usages; true");
        return true;
      }

      if (member instanceof PsiMethod && member.hasModifierProperty(PsiModifier.PUBLIC)) {
        String propertyName = PropertyUtilBase.getPropertyName(member);
        if (propertyName != null) {
          SearchScope fileScope = containingFile.getUseScope();
          if (fileScope instanceof GlobalSearchScope &&
              searchHelper.isCheapEnoughToSearch(propertyName, (GlobalSearchScope)fileScope, ignoreFile, progress) ==
              PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            log("* "+member.getName()+" too many prop usages; false");
            return false;
          }
        }
      }
    }
    FindUsagesOptions options;
    Collection<PsiMember> toSearch = new SmartList<>(member);
    if (member instanceof PsiPackage) {
      options = new JavaPackageFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = true;
    }
    else if (member instanceof PsiClass) {
      options = new JavaClassFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = true;
    }
    else if (member instanceof PsiMethod method) {
      options = new JavaMethodFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = method.isConstructor();
      toSearch.addAll(DeepestSuperMethodsSearch.search(method).findAll());
    }
    else if (member instanceof PsiVariable) {
      options = new JavaVariableFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = false;
    }
    else {
      options = new FindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = true;
    }
    options.isUsages = true;
    return ContainerUtil.process(toSearch, m -> JavaFindUsagesHelper.processElementUsages(m, options, usageInfoProcessor));
  }

  private static boolean isEnumValuesMethodUsed(@NotNull Project project,
                                                @NotNull PsiFile containingFile,
                                                @NotNull PsiMember member,
                                                @NotNull ProgressIndicator progress,
                                                @NotNull GlobalUsageHelper helper) {
    final PsiClass containingClass = member.getContainingClass();
    if (!(containingClass instanceof PsiClassImpl)) return true;
    final PsiMethod valuesMethod = ((PsiClassImpl)containingClass).getValuesMethod();
    return valuesMethod == null || isMethodReferenced(project, containingFile, valuesMethod, progress, helper);
  }

  private static boolean canBeReferencedViaWeirdNames(@NotNull PsiMember member, @NotNull PsiFile containingFile) {
    if (member instanceof PsiClass) return false;
    if (!(containingFile instanceof PsiJavaFile)) return true;  // Groovy field can be referenced from Java by getter
    if (member instanceof PsiField) return false;  //Java field cannot be referenced by anything but its name
    if (member instanceof PsiMethod) {
      return PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)member);  //Java accessors can be referenced by field name from Groovy
    }
    return false;
  }

  public static boolean isClassUsed(@NotNull Project project,
                                    @NotNull PsiFile containingFile,
                                    @NotNull PsiClass aClass,
                                    @NotNull ProgressIndicator progress,
                                    @NotNull GlobalUsageHelper helper) {
    Boolean result = helper.unusedClassCache.get(aClass);
    if (result == null) {
      result = isReallyUsed(project, containingFile, aClass, progress, helper);
      helper.unusedClassCache.put(aClass, result);
    }
    return result;
  }

  private static boolean isReallyUsed(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiClass aClass,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (isImplicitUsage(project, aClass) || helper.isLocallyUsed(aClass)) return true;
    if (helper.isCurrentFileAlreadyChecked()) {
      if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE) ||
             aClass.getParent() instanceof PsiDeclarationStatement ||
             aClass instanceof PsiTypeParameter) return false;
    }
    return !weAreSureThereAreNoUsages(project, containingFile, aClass, progress, helper);
  }

  private static boolean isIntentionalPrivateConstructor(@NotNull PsiMethod method, PsiClass containingClass) {
    return method.isConstructor() &&
           containingClass != null &&
           containingClass.getConstructors().length == 1;
  }
}

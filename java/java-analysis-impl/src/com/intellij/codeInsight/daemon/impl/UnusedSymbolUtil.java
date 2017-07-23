/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.find.findUsages.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnusedSymbolUtil {

  public static boolean isInjected(@NotNull Project project, @NotNull PsiModifierListOwner modifierListOwner) {
    return EntryPointsManagerBase.getInstance(project).isEntryPoint(modifierListOwner);
  }

  public static boolean isImplicitUsage(@NotNull Project project,
                                        @NotNull PsiModifierListOwner element,
                                        @Nullable ProgressIndicator progress) {
    if (isInjected(project, element)) return true;
    for (ImplicitUsageProvider provider : Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isImplicitRead(@NotNull PsiVariable variable) {
    return isImplicitRead(variable.getProject(), variable, null);
  }

  public static boolean isImplicitRead(@NotNull Project project, @NotNull PsiVariable element, @Nullable ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return isInjected(project, element);
  }

  public static boolean isImplicitWrite(@NotNull PsiVariable variable) {
    return isImplicitWrite(variable.getProject(), variable, null);
  }

  public static boolean isImplicitWrite(@NotNull Project project,
                                        @NotNull PsiVariable element,
                                        @Nullable ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)) {
      ProgressManager.checkCanceled();
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return EntryPointsManager.getInstance(project).isImplicitWrite(element);
  }

  @Nullable
  public static HighlightInfo createUnusedSymbolInfo(@NotNull PsiElement element,
                                                     @NotNull String message,
                                                     @NotNull final HighlightInfoType highlightInfoType) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element).descriptionAndTooltip(message).create();
    if (info == null) {
      return null; //filtered out
    }

    UnusedDeclarationFixProvider[] fixProviders = Extensions.getExtensions(UnusedDeclarationFixProvider.EP_NAME);
    for (UnusedDeclarationFixProvider provider : fixProviders) {
      IntentionAction[] fixes = provider.getQuickFixes(element);
      for (IntentionAction fix : fixes) {
        QuickFixAction.registerQuickFixAction(info, fix);
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
      if (isImplicitUsage(project, method, progress)) {
        return true;
      }
      if (!helper.isCurrentFileAlreadyChecked()) {
        return !weAreSureThereAreNoUsages(project, containingFile, method, progress, helper);
      }
    }
    else {
      //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
      boolean isConstructor = method.isConstructor();
      if (containingClass != null && isConstructor
          && containingClass.getConstructors().length == 1
          && isClassUsed(project, containingFile, containingClass, progress, helper)) {
        return true;
      }
      if (isImplicitUsage(project, method, progress)) return true;

      if (!isConstructor && FindSuperElementsHelper.findSuperElements(method).length != 0) {
        return true;
      }
      if (!weAreSureThereAreNoUsages(project, containingFile, method, progress, helper)) {
        return true;
      }
    }
    return false;
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

  private static void log(String s) {
    //System.out.println(s);
  }

  // return false if can't process usages (weird member of too may usages) or processor returned false
  public static boolean processUsages(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiMember member,
                                      @NotNull ProgressIndicator progress,
                                      @Nullable PsiFile ignoreFile,
                                      @NotNull Processor<UsageInfo> usageInfoProcessor) {
    String name = member.getName();
    if (name == null) {
      log("* "+member.getName()+" no name; false");
      return false;
    }
    SearchScope useScope = member.getUseScope();
    PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
    if (useScope instanceof GlobalSearchScope) {
      // some classes may have references from within XML outside dependent modules, e.g. our actions
      if (member instanceof PsiClass) {
        useScope = GlobalSearchScope.projectScope(project).uniteWith((GlobalSearchScope)useScope);
      }

      // if we've resolved all references, find usages will be fast
      PsiSearchHelper.SearchCostResult cheapEnough = RefResolveService.ENABLED && RefResolveService.getInstance(project).isUpToDate() ? PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES :
                                                     searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, ignoreFile, progress);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        log("* "+member.getName()+" too many usages; false");
        return false;
      }

      //search usages if it cheap
      //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES && !canBeReferencedViaWeirdNames(member, containingFile)) {
        log("* "+member.getName()+" 0 usages; true");
        return true;
      }

      if (member instanceof PsiMethod) {
        String propertyName = PropertyUtil.getPropertyName(member);
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
    if (member instanceof PsiPackage) {
      options = new JavaPackageFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = true;
    }
    else if (member instanceof PsiClass) {
      options = new JavaClassFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = true;
    }
    else if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      options = new JavaMethodFindUsagesOptions(useScope);
      options.isSearchForTextOccurrences = method.isConstructor();
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
    return JavaFindUsagesHelper.processElementUsages(member, options, usageInfoProcessor);
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
      return PropertyUtil.isSimplePropertyAccessor((PsiMethod)member);  //Java accessors can be referenced by field name from Groovy
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
    if (isImplicitUsage(project, aClass, progress) || helper.isLocallyUsed(aClass)) return true;
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

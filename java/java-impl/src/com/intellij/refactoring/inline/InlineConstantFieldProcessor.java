/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.inline;

import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class InlineConstantFieldProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineConstantFieldProcessor");
  private PsiField myField;
  private final PsiReferenceExpression myRefExpr;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInCommentsAndStrings;
  private final boolean mySearchForTextOccurrences;
  private final boolean myDeleteDeclaration;
  private Map<Language, InlineHandler.Inliner> myInliners;

  public InlineConstantFieldProcessor(PsiField field, Project project, PsiReferenceExpression ref, boolean isInlineThisOnly) {
    this(field, project, ref, isInlineThisOnly, false, false, true);
  }

  public InlineConstantFieldProcessor(PsiField field,
                                      Project project,
                                      PsiReferenceExpression ref,
                                      boolean isInlineThisOnly,
                                      boolean searchInCommentsAndStrings,
                                      boolean searchForTextOccurrences,
                                      boolean isDeleteDeclaration) {
    super(project);
    myField = field;
    myRefExpr = ref;
    myInlineThisOnly = isInlineThisOnly;
    mySearchInCommentsAndStrings = searchInCommentsAndStrings;
    mySearchForTextOccurrences = searchForTextOccurrences;
    myDeleteDeclaration = isDeleteDeclaration;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new InlineViewDescriptor(myField);
  }

  @Override
  protected boolean isPreviewUsages(@NotNull UsageInfo[] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for (UsageInfo info : usages) {
      if (info instanceof NonCodeUsageInfo) return true;
    }
    return false;
  }

  private static class UsageFromJavaDoc extends UsageInfo {
    private UsageFromJavaDoc(@NotNull PsiElement element) {
      super(element, true);
    }
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myRefExpr)};

    List<UsageInfo> usages = new ArrayList<>();
    for (PsiReference ref : ReferencesSearch.search(myField, GlobalSearchScope.projectScope(myProject), false)) {
      PsiElement element = ref.getElement();
      UsageInfo info = new UsageInfo(element);
      if (element instanceof PsiDocMethodOrFieldRef) {
        info = new UsageFromJavaDoc(element);
      }

      usages.add(info);
    }
    if (mySearchInCommentsAndStrings || mySearchForTextOccurrences) {
      UsageInfoFactory nonCodeUsageFactory = new NonCodeUsageInfoFactory(myField, myField.getName()){
        @Override
        public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
          if (PsiTreeUtil.isAncestor(myField, usage, false)) return null;
          return super.createUsageInfo(usage, startOffset, endOffset);
        }
      };
      if (mySearchInCommentsAndStrings) {
        String stringToSearch =
          ElementDescriptionUtil.getElementDescription(myField, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
        TextOccurrencesUtil.addUsagesInStringsAndComments(myField, stringToSearch, usages, nonCodeUsageFactory);
      }

      if (mySearchForTextOccurrences) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(myField, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil
          .addTextOccurences(myField, stringToSearch, GlobalSearchScope.projectScope(myProject), usages, nonCodeUsageFactory);
      }
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
    myField = (PsiField)elements[0];
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    PsiExpression initializer = InlineConstantFieldHandler.getInitializer(myField);
    LOG.assertTrue(initializer != null);
    final Set<PsiAssignmentExpression> assignments = new HashSet<>();
    for (UsageInfo info : usages) {
      if (info instanceof UsageFromJavaDoc) continue;
      if (info instanceof NonCodeUsageInfo) continue;
      final PsiElement element = info.getElement();
      if (element == null) continue;
      try {
        if (element instanceof PsiExpression) {
          inlineExpressionUsage((PsiExpression)element, initializer, assignments);
        }
        else {
          PsiImportStaticStatement importStaticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
          if (importStaticStatement != null) {
            importStaticStatement.delete();
          }
          else {
            GenericInlineHandler.inlineReference(info, myField, myInliners);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    for (PsiAssignmentExpression assignment : assignments) {
      assignment.delete();
    }

    if (!myInlineThisOnly && myDeleteDeclaration && myField.isWritable()) {
      try {
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myField);
    return data;
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.inline.field";
  }

  private void inlineExpressionUsage(PsiExpression expr,
                                     PsiExpression initializer1,
                                     Set<PsiAssignmentExpression> assignments) throws IncorrectOperationException {
    if (myField.isWritable()) {
      myField.normalizeDeclaration();
    }

    if (isAccessedForWriting(expr)) {
      PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(expr, PsiAssignmentExpression.class);
      if (assignmentExpression != null) {
        assignments.add(assignmentExpression);
      }
      return;
    }

    PsiExpression thisAccessExpr = expr instanceof PsiReferenceExpression ? ((PsiReferenceExpression)expr).getQualifierExpression() : null;
    PsiExpression invalidationCopy = thisAccessExpr != null ? (PsiExpression)thisAccessExpr.copy() : null;
    InlineUtil.inlineVariable(myField, initializer1, (PsiJavaCodeReferenceElement)expr, invalidationCopy);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("inline.field.command", DescriptiveNameUtil.getDescriptiveName(myField));
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    ReferencedElementsCollector collector = new ReferencedElementsCollector();
    PsiExpression initializer = InlineConstantFieldHandler.getInitializer(myField);
    LOG.assertTrue(initializer != null);
    initializer.accept(collector);
    HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myField.getProject()).getResolveHelper();
    for (UsageInfo info : usagesIn) {
      PsiElement element = info.getElement();
      if (element instanceof PsiExpression && (!myField.hasModifierProperty(PsiModifier.FINAL) || myInlineThisOnly) && isAccessedForWriting((PsiExpression)element)) {
        String message = RefactoringBundle.message("0.is.used.for.writing.in.1", RefactoringUIUtil.getDescription(myField, true),
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(element, message);
      }

      for (PsiMember member : referencedWithVisibility) {
        if (!resolveHelper.isAccessible(member, element, null)) {
          String message = RefactoringBundle.message("0.will.not.be.accessible.from.1.after.inlining", RefactoringUIUtil.getDescription(member, true),
                                                     RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
          conflicts.putValue(member, message);
        }
      }
    }

    myInliners = GenericInlineHandler.initInliners(myField, usagesIn, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return myInlineThisOnly;
      }
    }, conflicts, JavaLanguage.INSTANCE);

    if (!myInlineThisOnly) {
      for (UsageInfo info : usagesIn) {
        final PsiElement element = info.getElement();
        if (element instanceof PsiDocMethodOrFieldRef) {
          if (!PsiTreeUtil.isAncestor(myField, element, false)) {
            conflicts.putValue(element, "Inlined field is used in javadoc");
          }
        }
      }
    }

    return showConflicts(conflicts, usagesIn);
  }

  private static boolean isAccessedForWriting (PsiExpression expr) {
    while(expr.getParent() instanceof PsiArrayAccessExpression) {
      expr = (PsiExpression)expr.getParent();
    }

    return PsiUtil.isAccessedForWriting(expr);
  }

  @Override
  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.singletonList(myRefExpr);
    }
    else {
      if (!myField.isWritable()) return Collections.emptyList();
      return super.getElementsToWrite(descriptor);
    }
  }
}

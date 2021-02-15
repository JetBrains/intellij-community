// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class InlineConstantFieldProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InlineConstantFieldProcessor.class);
  private PsiField myField;
  private final PsiElement myRefExpr;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInCommentsAndStrings;
  private final boolean mySearchForTextOccurrences;
  private final boolean myDeleteDeclaration;
  @SuppressWarnings("LeakableMapKey") //short living refactoring 
  private Map<Language, InlineHandler.Inliner> myInliners;

  public InlineConstantFieldProcessor(PsiField field, Project project, PsiElement ref, boolean isInlineThisOnly) {
    this(field, project, ref, isInlineThisOnly, false, false, true);
  }

  public InlineConstantFieldProcessor(PsiField field,
                                      Project project,
                                      PsiElement ref,
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
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myField);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for (UsageInfo info : usages) {
      if (info instanceof NonCodeUsageInfo) return true;
    }
    return false;
  }

  private static final class UsageFromJavaDoc extends UsageInfo {
    private UsageFromJavaDoc(@NotNull PsiElement element) {
      super(element, true);
    }
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myRefExpr)};

    List<UsageInfo> usages = new ArrayList<>();
    for (PsiReference ref : ReferencesSearch.search(myField, myRefactoringScope, false)) {
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
        TextOccurrencesUtil.addUsagesInStringsAndComments(myField, myRefactoringScope, stringToSearch, usages, nonCodeUsageFactory);
      }

      if (mySearchForTextOccurrences && myRefactoringScope instanceof GlobalSearchScope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(myField, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(myField, stringToSearch, (GlobalSearchScope)myRefactoringScope,
                                               usages, nonCodeUsageFactory);
      }
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
    myField = (PsiField)elements[0];
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
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
                                     Set<? super PsiAssignmentExpression> assignments) throws IncorrectOperationException {
    if (expr instanceof PsiLiteralExpression) {
      // Possible reflective usage
      return;
    }
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
    return JavaRefactoringBundle.message("inline.field.command", DescriptiveNameUtil.getDescriptiveName(myField));
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, @Nls String> conflicts = new MultiMap<>();

    ReferencedElementsCollector collector = new ReferencedElementsCollector();
    PsiExpression initializer = InlineConstantFieldHandler.getInitializer(myField);
    LOG.assertTrue(initializer != null);
    initializer.accept(collector);
    HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

    if (!myField.hasInitializer()) {
      boolean dependsOnContext;
      PsiMethod[] constructors = Objects.requireNonNull(myField.getContainingClass()).getConstructors();
      if (constructors.length == 1) {
        Ref<PsiElement> reference = new Ref<>();
        dependsOnContext = !PsiTreeUtil.processElements(initializer, element -> {
          if (element instanceof PsiJavaCodeReferenceElement) {
            PsiElement resolve = ((PsiJavaCodeReferenceElement)element).resolve();
            if (resolve != null &&
                PsiTreeUtil.isAncestor(constructors[0], resolve, true) && 
                !PsiTreeUtil.isAncestor(initializer, resolve, true)) {
              reference.set(resolve);
              return false;
            }
          }
          return true;
        });
        if (dependsOnContext) {
          for (UsageInfo usageInfo : usagesIn) {
            PsiElement element = usageInfo.getElement();
            if (element != null && !PsiTreeUtil.isAncestor(constructors[0], element, true)) {
              conflicts.putValue(element, JavaRefactoringBundle.message("inline.field.initializer.is.not.accessible",
                                                                        RefactoringUIUtil.getDescription(reference.get(), false),
                                                                        RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true)));
            }
          }
        }
      }
    }

    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myField.getProject()).getResolveHelper();
    for (UsageInfo info : usagesIn) {
      PsiElement element = info.getElement();
      if (element instanceof PsiExpression && (!myField.hasModifierProperty(PsiModifier.FINAL) || myInlineThisOnly) && isAccessedForWriting((PsiExpression)element)) {
        String message = JavaRefactoringBundle.message("0.is.used.for.writing.in.1", RefactoringUIUtil.getDescription(myField, true),
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(element, message);
      }

      for (PsiMember member : referencedWithVisibility) {
        if (!resolveHelper.isAccessible(member, element, null)) {
          String message = JavaRefactoringBundle.message("0.will.not.be.accessible.from.1.after.inlining", RefactoringUIUtil.getDescription(member, true),
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
            conflicts.putValue(element, JavaRefactoringBundle.message("inline.field.used.in.javadoc"));
          }
        }
        if (element instanceof PsiLiteralExpression &&
            ContainerUtil.or(element.getReferences(), JavaLangClassMemberReference.class::isInstance)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.field.used.in.reflection"));
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

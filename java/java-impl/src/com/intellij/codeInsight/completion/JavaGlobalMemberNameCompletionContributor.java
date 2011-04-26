package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.CLASS_NAME) {
      return;
    }

    if (result.getPrefixMatcher().getPrefix().length() == 0) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
    if (referenceExpression.isQualified()) {
      return;
    }

    completeStaticMembers(position).processStaticMethodsGlobally(result);
  }

  public static StaticMemberProcessor completeStaticMembers(final PsiElement position) {
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull final PsiClass containingClass, final boolean shouldImport) {
        if (member instanceof PsiMethod) {
          final JavaMethodCallElement element = new JavaMethodCallElement((PsiMethod)member, true, false);
          element.setShouldBeImported(shouldImport);
          return element;
        }
        return new StaticFieldLookupItem((PsiField)member, shouldImport, containingClass);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        final JavaMethodCallElement element = new JavaMethodCallElement(overloads.get(0), true, true);
        element.putUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, overloads);
        element.setShouldBeImported(shouldImport);
        return element;
      }
    };
    final PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          processor.importMembersOf(statement.resolveTargetClass());
        }
      }
    }

    return processor;
  }

  private static class StaticFieldLookupItem extends VariableLookupItem implements StaticallyImportable {
    private final MemberLookupHelper myHelper;
    private final PsiClass myContainingClass;

    public StaticFieldLookupItem(PsiField field, boolean shouldImport, PsiClass containingClass) {
      super(field);
      myContainingClass = containingClass;
      myHelper = new MemberLookupHelper(field, containingClass, shouldImport, false);
    }

    @Override
    public void setShouldBeImported(boolean shouldImportStatic) {
      myHelper.setShouldBeImported(shouldImportStatic);
    }

    @Override
    public boolean canBeImported() {
      return true;
    }

    @Override
    public boolean willBeImported() {
      return myHelper.willBeImported();
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      myHelper.renderElement(presentation, getAttribute(FORCE_QUALIFY) != null, PsiSubstitutor.EMPTY);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      if (willBeImported()) {
        context.commitDocument();
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
        if (ref != null) {
          ref.bindToElementViaStaticImport(myContainingClass);
          PostprocessReformattingAspect.getInstance(ref.getProject()).doPostponedFormatting();
        }
      }
      super.handleInsert(context);
    }

    @Override
    protected boolean shouldQualify(PsiField field, InsertionContext context) {
      return !willBeImported() || super.shouldQualify(field, context);
    }
  }
}

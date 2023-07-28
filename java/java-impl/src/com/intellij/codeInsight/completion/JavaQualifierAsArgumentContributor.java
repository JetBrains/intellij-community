// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.util.PsiFormatUtil.formatVariable;
import static com.intellij.psi.util.PsiFormatUtilBase.MAX_PARAMS_TO_SHOW;

@ApiStatus.Experimental
public class JavaQualifierAsArgumentContributor extends CompletionContributor implements DumbAware {

  private static final int MAX_SIZE = 100;

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    result.runRemainingContributors(parameters, true);
    if (!Registry.is("java.completion.qualifier.as.argument")) {
      return;
    }

    if (parameters.getCompletionType() != CompletionType.BASIC || parameters.getInvocationCount() < 1) {
      return;
    }
    PsiElement position = parameters.getPosition();
    if (!(JavaKeywordCompletion.AFTER_DOT.accepts(position))) {
      return;
    }
    if (!(position.getParent() instanceof PsiReferenceExpression psiReferenceExpression)) {
      return;
    }
    PsiExpression qualifierExpression = psiReferenceExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return;
    }
    PrefixMatcher matcher = result.getPrefixMatcher();
    Project project = parameters.getEditor().getProject();
    if (project == null) return;

    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);
    MyStaticMembersProcessor processor = new MyStaticMembersProcessor(parameters, qualifierExpression);
    HashSet<PsiClass> classesToSkip = new HashSet<>();

    shortNamesCache.processAllMethodNames(new Processor<>() {
      private int size = 0;

      @Override
      public boolean process(String name) {
        if (!matcher.prefixMatches(name)) {
          return true;
        }
        ProgressManager.checkCanceled();
        shortNamesCache.processMethodsWithName(name, position.getResolveScope(), method -> {
          processor.processStaticMember(element -> {
            size++;
            result.consume(element);
          }, method, classesToSkip);
          return size < MAX_SIZE;
        });
        return size < MAX_SIZE;
      }
    }, position.getResolveScope(), null);
  }

  private static final class MyStaticMembersProcessor extends JavaStaticMemberProcessor {

    @NotNull
    private final PsiExpression myOldQualifiedExpression;
    @Nullable
    private final PsiElement myOriginalPosition;

    private MyStaticMembersProcessor(@NotNull CompletionParameters parameters,
                                     @NotNull PsiExpression oldQualifiedExpression) {
      super(parameters);
      this.myOldQualifiedExpression = oldQualifiedExpression;
      this.myOriginalPosition = parameters.getOriginalPosition();
    }

    @Override
    public void processStaticMember(@NotNull Consumer<? super LookupElement> consumer, PsiMember member, Set<PsiClass> classesToSkip) {
      if (!(member instanceof PsiMethod method) || !filter(method)) {
        return;
      }
      super.processStaticMember(consumer, member, classesToSkip);
    }

    @Override
    protected @NotNull JavaMethodCallElement getMethodCallElement(boolean shouldImport, List<? extends PsiMethod> members) {
      PsiMethod method = members.get(0);
      boolean shouldImportOrQualify = true;
      if (myOriginalPosition!=null && PsiTreeUtil.isAncestor(method.getContainingClass(), myOriginalPosition, true)) {
        shouldImportOrQualify = false;
      }
      return new JavaQualifierAsParameterMethodCallElement(members.stream().filter(t -> filter(t)).toList(), myOldQualifiedExpression,
                                                           shouldImport, shouldImportOrQualify);
    }

    private boolean filter(PsiMethod member) {
      PsiParameterList list = member.getParameterList();
      if (list.isEmpty()) {
        return false;
      }
      PsiParameter parameter = list.getParameter(0);
      if (parameter == null) {
        return false;
      }
      PsiType paramType = PsiTypesUtil.getParameterType(list.getParameters(), 0, true);
      paramType = TypeConversionUtil.erasure(paramType);
      if (paramType == null) {
        return false;
      }
      return TypeConversionUtil.areTypesAssignmentCompatible(paramType, myOldQualifiedExpression);
    }
  }

  private static class JavaQualifierAsParameterMethodCallElement extends JavaMethodCallElement {
    private final PsiExpression myOldQualifierExpression;

    private final Collection<? extends PsiMethod> myMethods;
    private final boolean myShouldImport;
    private final boolean myShouldImportOrQualify;
    private final boolean myMergedOverloads;

    private JavaQualifierAsParameterMethodCallElement(@NotNull Collection<? extends PsiMethod> methods,
                                                      @NotNull PsiExpression oldQualifierExpression,
                                                      boolean shouldImportStatic,
                                                      boolean shouldImportOrQualify) {
      super(methods.iterator().next(), shouldImportStatic, methods.size() > 1);
      myMethods = methods;
      myMergedOverloads = methods.size() > 1;
      myOldQualifierExpression = oldQualifierExpression;
      myShouldImport = shouldImportStatic;
      myShouldImportOrQualify = shouldImportOrQualify;
    }

    @Override
    protected boolean needImportOrQualify() {
      return myShouldImportOrQualify;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.STATIC_COMPLETION);
      super.handleInsert(context);
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this));

      presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));
      PsiMethod method = myMethods.iterator().next();
      PsiClass containingClass = method.getContainingClass();
      final String className = containingClass == null ? "???" : containingClass.getName();
      final String memberName = method.getName();
      if (StringUtil.isNotEmpty(className)) {
        presentation.setItemText(className + "." + memberName);
      }
      else {
        presentation.setItemText(memberName);
      }

      final String qname = containingClass == null ? "" : containingClass.getQualifiedName();
      String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
      String location = myShouldImport && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

      final String paramsText;
      boolean allHasOneArgument = ContainerUtil.all(myMethods, m -> m.getParameterList().getParameters().length == 1);
      if (myMergedOverloads) {
        if (allHasOneArgument) {
          paramsText = "(" + myOldQualifierExpression.getText() + ")";
        }
        else {
          paramsText = "(" + myOldQualifierExpression.getText() + ", ...)";
        }
      }
      else {
        StringBuilder builder = new StringBuilder();
        PsiParameter[] params = method.getParameterList().getParameters();

        for (int i = 1; i < Math.min(params.length, MAX_PARAMS_TO_SHOW); i++) {
          builder.append(", ");
          builder.append(formatVariable(params[i], PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY));
        }
        if (params.length > MAX_PARAMS_TO_SHOW) {
          builder.append(", ...");
        }
        paramsText = "(" + myOldQualifierExpression.getText() + builder + ")";
      }

      presentation.appendTailText(paramsText, false);
      if (myShouldImport && StringUtil.isNotEmpty(className)) {
        presentation.appendTailText(" in " + className + location, true);
      }

      if (!myMergedOverloads) {
        PsiType type = MemberLookupHelper.getDeclaredType(method, getSubstitutor());
        if (type != null) {
          presentation.setTypeText(type.getPresentableText());
        }
      }
    }

    @Override
    protected void beforeHandle(@NotNull InsertionContext context) {
      TextRange range = myOldQualifierExpression.getTextRange();
      context.getDocument().deleteString(range.getStartOffset(), range.getEndOffset() + 1);
      context.commitDocument();
    }

    @Override
    protected boolean canStartArgumentLiveTemplate() {
      return false;
    }

    @Override
    protected void afterHandle(@NotNull InsertionContext context, @Nullable PsiCallExpression call) {
      context.commitDocument();
      PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
      if (call != null) {
        PsiExpressionList list = call.getArgumentList();
        if (list != null) {
          TextRange argumentList = list.getTextRange();
          String text = myOldQualifierExpression.getText();
          boolean hasOneArgument = ContainerUtil.exists(myMethods, method -> method.getParameterList().getParameters().length == 1);
          if (!hasOneArgument) {
            text += ", ";
          }
          context.getDocument().insertString(argumentList.getStartOffset() + 1, text);
          context.getEditor().getCaretModel().moveToOffset(argumentList.getStartOffset() + 1 + text.length());
        }
      }
    }
  }
}
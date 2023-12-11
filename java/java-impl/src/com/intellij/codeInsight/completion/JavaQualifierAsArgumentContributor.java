// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.JavaStaticMethodNameCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.util.PsiFormatUtil.formatVariable;
import static com.intellij.psi.util.PsiFormatUtilBase.MAX_PARAMS_TO_SHOW;

@ApiStatus.Experimental
public class JavaQualifierAsArgumentContributor extends CompletionContributor implements DumbAware {

  private static final int MAX_SIZE = 50;

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    result.runRemainingContributors(parameters, true);
    fillQualifierAsArgumentContributor(parameters, result);
  }

  static void fillQualifierAsArgumentContributor(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (!AdvancedSettings.getBoolean("java.completion.qualifier.as.argument")) {
      return;
    }

    if ((parameters.getCompletionType() != CompletionType.BASIC && parameters.getCompletionType() != CompletionType.SMART) ||
        parameters.getInvocationCount() < 3) {
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
    JavaQualifierAsArgumentStaticMembersProcessor
      processor = new JavaQualifierAsArgumentStaticMembersProcessor(parameters, qualifierExpression);
    process(parameters, matcher, processor, result);
  }

  private static void process(@NotNull CompletionParameters parameters,
                              @NotNull PrefixMatcher matcher,
                              @NotNull JavaQualifierAsArgumentContributor.JavaQualifierAsArgumentStaticMembersProcessor processor,
                              @NotNull CompletionResultSet result) {

    PsiElement position = parameters.getPosition();
    PsiFile file = position.getContainingFile();
    if (file == null) {
      return;
    }
    GlobalSearchScope scope = position.getResolveScope();
    Project project = position.getProject();
    if (project.isDefault()) {
      return;
    }
    Processor<PsiMember> psiStaticMethodProcessor = new Processor<>() {
      private int size = 0;
      private final MultiMap<PsiClass, String> classesToSkip = new MultiMap<>();

      @Override
      public boolean process(PsiMember member) {
        ProgressManager.checkCanceled();
        String name = member.getName();
        if (name == null || !matcher.prefixMatches(name)) {
          return true;
        }
        if (!(member instanceof PsiMethod method && method.hasModifier(JvmModifier.STATIC))) {
          return true;
        }
        PsiFile currentFile = method.getContainingFile();
        if (method.hasModifier(JvmModifier.PRIVATE) && !file.isEquivalentTo(currentFile)) {
          return true;
        }
        PsiClass containingClass = method.getContainingClass();
        String methodName = method.getName();
        if (containingClass == null) {
          return true;
        }
        Collection<String> names = classesToSkip.get(containingClass);
        if (names.contains(methodName)) {
          return true;
        }
        processor.processStaticMember(element -> {
          size++;
          result.consume(element);
          classesToSkip.putValue(containingClass, methodName);
        }, member, new HashSet<>());
        return size < MAX_SIZE;
      }
    };

    PsiElement originalPosition = parameters.getOriginalPosition();
    if (originalPosition != null) {
      PsiClass nextClass = PsiTreeUtil.getParentOfType(originalPosition, PsiClass.class);
      while (nextClass != null) {
        PsiMethod[] methods = nextClass.getMethods();
        for (PsiMethod method : methods) {
          ProgressManager.checkCanceled();
          if (!psiStaticMethodProcessor.process(method)) {
            return;
          }
        }
        nextClass = PsiTreeUtil.getParentOfType(nextClass, PsiClass.class);
      }
    }

    List<JavaStaticMethodNameCache> staticList = JavaStaticMethodNameCache.EP_NAME.getExtensionList(project);
    Set<Class<? extends PsiShortNamesCache>> used = new HashSet<>();
    for (JavaStaticMethodNameCache cache : staticList) {
      ProgressManager.checkCanceled();
      used.add(cache.replaced());
      boolean next = cache.processMethodsWithName(name -> matcher.prefixMatches(name), method -> {
        ProgressManager.checkCanceled();
        return psiStaticMethodProcessor.process(method);
      }, scope, null);
      if (!next) {
        return;
      }
    }

    List<PsiShortNamesCache> list = PsiShortNamesCache.EP_NAME.getExtensionList(project);
    AtomicBoolean stop = new AtomicBoolean(false);
    for (PsiShortNamesCache cache : list) {
      //skip processed
      if (used.contains(cache.getClass())) {
        continue;
      }
      if (stop.get()) {
        break;
      }
      List<String> allNames = new ArrayList<>();
      cache.processAllMethodNames(name -> {
        if (!matcher.prefixMatches(name)) {
          return true;
        }
        allNames.add(name);
        return true;
      }, scope, null);
      for (String name : allNames) {
        cache.processMethodsWithName(name, method -> {
          ProgressManager.checkCanceled();
          boolean continueProcess = psiStaticMethodProcessor.process(method);
          stop.set(!continueProcess);
          return !stop.get();
        }, scope, null);
      }
    }
  }


  static final class JavaQualifierAsArgumentStaticMembersProcessor extends JavaStaticMemberProcessor {

    @NotNull
    private final PsiExpression myOldQualifiedExpression;
    @Nullable
    private final PsiElement myOriginalPosition;
    private final boolean isSmart;

    @NotNull
    private final NotNullLazyValue<Collection<PsiType>> myExpectedTypes;

    @NotNull
    private static final CallMatcher MY_SKIP_METHODS =
      CallMatcher.anyOf(
        CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "format")
      );


    JavaQualifierAsArgumentStaticMembersProcessor(@NotNull CompletionParameters parameters,
                                                  @NotNull PsiExpression oldQualifiedExpression) {
      super(parameters);
      this.myOldQualifiedExpression = oldQualifiedExpression;
      this.myOriginalPosition = parameters.getOriginalPosition();
      this.myExpectedTypes =
        NotNullLazyValue.createValue(
          () -> ContainerUtil.map(JavaSmartCompletionContributor.getExpectedTypes(parameters),
                                  (ExpectedTypeInfo info) -> {
                                    PsiType type = info.getType();
                                    return FunctionalInterfaceParameterizationUtil.getGroundTargetType(type);
                                  }));
      isSmart = parameters.getCompletionType() == CompletionType.SMART;
    }

    @Override
    protected boolean additionalFilter(PsiMember member) {
      return (member instanceof PsiMethod method && filter(method) && !MY_SKIP_METHODS.methodMatches(method));
    }

    @Override
    protected @NotNull JavaMethodCallElement getMethodCallElement(boolean shouldImport, List<? extends PsiMethod> members) {
      PsiMethod method = members.get(0);
      boolean shouldImportOrQualify = true;
      boolean shouldShowClass = true;
      if (myOriginalPosition != null && PsiTreeUtil.isAncestor(method.getContainingClass(), myOriginalPosition, true)) {
        shouldImportOrQualify = false;
        shouldShowClass = false;
      }
      if (myOriginalPosition != null && ImportsUtil.hasStaticImportOn(myOriginalPosition, method, true)) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(myOriginalPosition, PsiClass.class);
        if (psiClass != null &&
            !PsiTreeUtil.isAncestor(psiClass, method, true) &&
            !ContainerUtil.and(psiClass.getAllMethods(), containingMethod -> method.getName().equals(containingMethod.getName()))) {
          shouldImportOrQualify = false;
        }
      }
      return new JavaQualifierAsParameterMethodCallElement(members.stream().filter(t -> filter(t)).toList(), myOldQualifiedExpression,
                                                           shouldImport, shouldImportOrQualify, shouldShowClass, isSmart);
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
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      for (PsiTypeParameter typeParameter : member.getTypeParameters()) {
        PsiClass[] supers = typeParameter.getSupers();
        PsiType[] types = typeParameter.getSuperTypes();
        if (ContainerUtil.or(supers, s -> s instanceof PsiTypeParameter && InheritanceUtil.getCircularClass(s) != null)) {
          types = new PsiType[]{TypeConversionUtil.erasure(types[0])};
        }
        PsiType composite = PsiIntersectionType.createIntersection(true, types);

        substitutor = substitutor.put(typeParameter, PsiWildcardType.createExtends(typeParameter.getManager(), composite));
      }
      PsiType targetType = substitutor.substitute(paramType);
      if (!TypeConversionUtil.areTypesAssignmentCompatible(targetType, myOldQualifiedExpression)) {
        return false;
      }
      if (myOldQualifiedExpression instanceof PsiLiteralExpression literalExpression &&
          PsiTypes.nullType().equals(literalExpression.getType())) {
        if (NullableNotNullManager.isNotNull(parameter)) {
          return false;
        }
      }
      if (!isSmart) {
        return true;
      }
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(parameter.getType());
      if (psiClass instanceof PsiTypeParameter typeParameter) {
        substitutor = substitutor.put(typeParameter, myOldQualifiedExpression.getType());
      }
      PsiType returnType = member.getReturnType();
      returnType = substitutor.substitute(returnType);
      if (returnType == null) {
        return false;
      }
      for (PsiType type : myExpectedTypes.getValue()) {
        if (TypeConversionUtil.isAssignable(type, returnType)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class JavaQualifierAsParameterMethodCallElement extends JavaMethodCallElement {
    private final PsiExpression myOldQualifierExpression;

    private final Collection<? extends PsiMethod> myMethods;
    private final boolean myShouldImportOrQualify;
    private final boolean myMergedOverloads;
    private final boolean myShouldShowClass;
    private final boolean myIsSmart;

    private JavaQualifierAsParameterMethodCallElement(@NotNull Collection<? extends PsiMethod> methods,
                                                      @NotNull PsiExpression oldQualifierExpression,
                                                      boolean shouldImportStatic,
                                                      boolean shouldImportOrQualify,
                                                      boolean shouldShowClass,
                                                      boolean isSmart) {
      super(methods.iterator().next(), shouldImportStatic, methods.size() > 1);
      myMethods = methods;
      myMergedOverloads = methods.size() > 1;
      myOldQualifierExpression = oldQualifierExpression;
      myShouldImportOrQualify = shouldImportOrQualify;
      myShouldShowClass = shouldShowClass;
      myIsSmart = isSmart;
    }

    @Override
    protected boolean needImportOrQualify() {
      return myShouldImportOrQualify;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      JavaContributorCollectors.logInsertHandle(context.getProject(), JavaContributorCollectors.STATIC_QUALIFIER_TYPE,
                                                myIsSmart ? CompletionType.SMART : CompletionType.BASIC);
      super.handleInsert(context);
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this));

      presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));
      PsiMethod method = myMethods.iterator().next();
      PsiClass containingClass = method.getContainingClass();
      final String className =
        containingClass == null || !myShouldShowClass ? "" : containingClass.getName();
      final String memberName = method.getName();
      if (StringUtil.isNotEmpty(className)) {
        presentation.setItemText(className + "." + memberName);
      }
      else {
        presentation.setItemText(memberName);
      }

      final String qname = containingClass == null ? "" : containingClass.getQualifiedName();
      String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
      String location = myShouldImportOrQualify && StringUtil.isNotEmpty(pkg) ? " " + pkg : "";

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
      if (myShouldImportOrQualify && StringUtil.isNotEmpty(className)) {
        presentation.appendTailText(" in " + location, true);
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
            CommonCodeStyleSettings codeStyleSettings = CodeStyle.getLanguageSettings(myOldQualifierExpression.getContainingFile());
            text += ",";
            if (codeStyleSettings.SPACE_AFTER_COMMA) {
              text += " ";
            }
          }
          context.getDocument().insertString(argumentList.getStartOffset() + 1, text);
          context.getEditor().getCaretModel().moveToOffset(argumentList.getStartOffset() + 1 + text.length());
        }
      }
    }
  }
}
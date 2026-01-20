// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.MemberLookupHelper;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModLaunchEditorAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@NotNullByDefault
final class MethodCallCompletionItem extends PsiUpdateCompletionItem<PsiMethod> {
  private final @Nullable PsiClass myContainingClass;
  private final PsiMethod myMethod;
  private final @Nullable MemberLookupHelper myHelper;
  private final boolean myNegatable;
  private final PsiSubstitutor myQualifierSubstitutor;
  private final PsiSubstitutor myInferenceSubstitutor;
  private final boolean myNeedExplicitTypeParameters;
  private final String myForcedQualifier;
  private final @NlsSafe String myPresentableTypeArgs;
  private final @Nullable String myAdditionalLookup;

  MethodCallCompletionItem(PsiMethod method) {
    this(method, null, null);
  }

  private MethodCallCompletionItem(PsiMethod method, @Nullable MemberLookupHelper helper, @Nullable String additionalLookup) {
    this(method, helper, additionalLookup, PsiSubstitutor.EMPTY, PsiSubstitutor.EMPTY, "", false, "");
  }
    
  private MethodCallCompletionItem(PsiMethod method, @Nullable MemberLookupHelper helper, @Nullable String additionalLookup,
                                   PsiSubstitutor qualifierSubstitutor, PsiSubstitutor inferenceSubstitutor,
                                   String forcedQualifier, boolean needExplicitTypeParameters, @NlsSafe String presentableTypeArgs) {
    super(forcedQualifier + method.getName(), method);
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myHelper = helper;
    PsiType type = method.getReturnType();
    myNegatable = type != null && PsiTypes.booleanType().isAssignableFrom(type);
    myAdditionalLookup = additionalLookup;
    myQualifierSubstitutor = qualifierSubstitutor;
    myInferenceSubstitutor = inferenceSubstitutor;
    myForcedQualifier = forcedQualifier;
    myNeedExplicitTypeParameters = needExplicitTypeParameters;
    myPresentableTypeArgs = presentableTypeArgs;
  }

  MethodCallCompletionItem(PsiMethod method, boolean shouldImportStatic, boolean mergedOverloads) {
    this(method, new MemberLookupHelper(method, method.getContainingClass(), shouldImportStatic || method.isConstructor(), mergedOverloads),
         computeQualifiedLookup(method, shouldImportStatic));
  }

  private static @Nullable String computeQualifiedLookup(PsiMethod method, boolean shouldImportStatic) {
    PsiClass myContainingClass = method.getContainingClass();
    if (!shouldImportStatic && !method.isConstructor()) {
      if (myContainingClass != null) {
        String className = myContainingClass.getName();
        if (className != null) {
          return className + "." + method.getName();
        }
      }
    }
    return null;
  }

  public @Nullable PsiType getType() {
    PsiType type = MemberLookupHelper.patchGetClass(myMethod, myInferenceSubstitutor.substitute(myMethod.getReturnType()));
    return getSubstitutor().substitute(type);
  }

  MethodCallCompletionItem withForcedQualifier(String forcedQualifier) {
    return new MethodCallCompletionItem(myMethod, myHelper, myAdditionalLookup, myQualifierSubstitutor, myInferenceSubstitutor,
                                        forcedQualifier, myNeedExplicitTypeParameters, myPresentableTypeArgs);
  }
  
  MethodCallCompletionItem withQualifierSubstitutor(PsiSubstitutor substitutor) {
    return new MethodCallCompletionItem(myMethod, myHelper, myAdditionalLookup, substitutor, myInferenceSubstitutor,
                                        myForcedQualifier, myNeedExplicitTypeParameters, myPresentableTypeArgs);
  }

  MethodCallCompletionItem withExpectedType(PsiElement place, PsiType expectedType) {
    PsiSubstitutor qualifierSubstitutor, inferenceSubstitutor;
    boolean needExplicitTypeParameters;
    if (myMethod.isConstructor()) {
      if (expectedType instanceof PsiClassType) {
        PsiClassType genericType = GenericsUtil.getExpectedGenericType(place, myContainingClass, (PsiClassType)expectedType);
        PsiSubstitutor substitutor = genericType.resolveGenerics().getSubstitutor();
        qualifierSubstitutor = inferenceSubstitutor = substitutor;
      } else {
        qualifierSubstitutor = inferenceSubstitutor = PsiSubstitutor.EMPTY;
      }
      needExplicitTypeParameters = false;
    } else {
      qualifierSubstitutor = myQualifierSubstitutor;
      inferenceSubstitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(myMethod, expectedType);
      needExplicitTypeParameters = JavaMethodCallElement.mayNeedTypeParameters(place) && SmartCompletionDecorator.hasUnboundTypeParams(myMethod, expectedType);
    }
    String presentableTypeArgs = needExplicitTypeParameters ?
                                 Objects.requireNonNullElse(JavaMethodCallElement.getTypeParamsText(true, myMethod, inferenceSubstitutor), "") : "";
    if (presentableTypeArgs.length() > 10) {
      presentableTypeArgs = presentableTypeArgs.substring(0, 10) + "...>";
    }
    return new MethodCallCompletionItem(myMethod, myHelper, myAdditionalLookup, qualifierSubstitutor, inferenceSubstitutor,
                                        myForcedQualifier, needExplicitTypeParameters, presentableTypeArgs);
  }

  @Override
  public AutoCompletionPolicy autoCompletionPolicy() {
    return myHelper != null && myHelper.willBeImported() ? AutoCompletionPolicy.NEVER_AUTOCOMPLETE : super.autoCompletionPolicy();
  }

  PsiSubstitutor getSubstitutor() {
    return myQualifierSubstitutor;
  }

  PsiSubstitutor getInferenceSubstitutor() {
    return myInferenceSubstitutor;
  }

  @Override
  public Set<@NlsSafe String> additionalLookupStrings() {
    return myAdditionalLookup == null ? Set.of() : Set.of(myAdditionalLookup);
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myInferenceSubstitutor.isValid() && myQualifierSubstitutor.isValid();
  }
  
  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    ThreeState parameters = mayHaveParameters(updater.getPsiFile());
    insertParentheses(updater, parameters);
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(updater.getDocument());
    if (myNeedExplicitTypeParameters) {
      qualifyMethodCall(updater, actionContext.offset());
      insertExplicitTypeParameters(updater);
    } else {
      importOrQualify(updater, actionContext.offset());
    }
    if (parameters != ThreeState.NO) {
      updater.editorAction(ModLaunchEditorAction.ACTION_PARAMETER_INFO, true);
    }
  }

  private void importOrQualify(ModPsiUpdater updater, int startOffset) {
    if (myHelper == null) return;
    if (myHelper.willBeImported()) {
      PsiReferenceExpression
        ref = PsiTreeUtil.findElementOfClassAtOffset(updater.getPsiFile(), startOffset - 1, PsiReferenceExpression.class, false);
      if (ref != null && myContainingClass != null && !ref.isReferenceTo(myMethod)) {
        ref.bindToElementViaStaticImport(myContainingClass);
      }
      return;
    }
    qualifyMethodCall(updater, startOffset);
  }

  private void insertExplicitTypeParameters(ModPsiUpdater updater) {
    Document document = updater.getDocument();
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);

    String typeParams = JavaMethodCallElement.getTypeParamsText(false, myMethod, myInferenceSubstitutor);
    if (typeParams != null) {
      PsiMethodCallExpression call =
        PsiTreeUtil.getParentOfType(updater.getPsiFile().findElementAt(updater.getCaretOffset() - 1), PsiMethodCallExpression.class);
      if (call != null) {
        PsiReferenceParameterList list = call.getMethodExpression().getParameterList();
        if (list != null) {
          PsiMethodCallExpression stub = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(updater.getProject())
            .createExpressionFromText("a." + typeParams + "b()", call);
          JavaCodeStyleManager.getInstance(updater.getProject())
            .shortenClassReferences(list.replace(Objects.requireNonNull(stub.getMethodExpression().getParameterList())));
        }
      }
    }
  }

  private void qualifyMethodCall(ModPsiUpdater updater, int startOffset) {
    PsiFile file = updater.getPsiFile();
    PsiReference reference = file.findReferenceAt(startOffset - 1);
    if (reference instanceof PsiReferenceExpression ref && ref.isQualified()) {
      return;
    }

    Document document = updater.getDocument();
    if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.");
      return;
    }

    if (myContainingClass == null) return;

    document.insertString(startOffset, ".");
    JavaCompletionUtil.insertClassReference(myContainingClass, file, startOffset);
  }

  private static void insertParentheses(ModNavigator updater, ThreeState mayHaveParameters) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings != null && !settings.isInsertParenthesesAutomatically()) return;
    boolean needRightParenth = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CommonCodeStyleSettings styleSettings = CodeStyle.getLanguageSettings(updater.getPsiFile(), JavaLanguage.INSTANCE);
    boolean spaceBetweenParentheses = mayHaveParameters == ThreeState.YES && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES ||
                                  mayHaveParameters == ThreeState.UNSURE && styleSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES;
    String parens = "";
    int inparens = 1;
    if (styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) {
      parens = " ";
      inparens++;
    }
    if (spaceBetweenParentheses) {
      parens += "(  ";
      inparens++;
    } else {
      parens += "(";
    }
    if (needRightParenth) {
      parens += ")";
    }
    int caret = updater.getCaretOffset();
    updater.getDocument().insertString(caret, parens);
    if (mayHaveParameters == ThreeState.NO) {
      updater.moveCaretTo(caret + parens.length());
    } else {
      int inParensOffset = caret + inparens;
      updater.moveCaretTo(inParensOffset);
      if (needRightParenth) {
        updater.registerTabOut(TextRange.create(inParensOffset, inParensOffset), caret + parens.length());
      }
    }
  }
  
  private ThreeState mayHaveParameters(PsiElement context) {
    if (myHelper != null && myHelper.isMergedOverloads() && myContainingClass != null) {
      PsiMethod[] methods = myContainingClass.findMethodsByName(myMethod.getName(), true);
      Project project = myContainingClass.getProject();
      boolean withParameters = false;
      boolean withoutParameters = false;
      for (PsiMethod method : methods) {
        if (PsiResolveHelper.getInstance(project).isAccessible(method, context, null)) {
          if (method.hasParameters()) {
            withParameters = true;
          } else {
            withoutParameters = true;
          }
          if (withParameters && withoutParameters) break;
        }
      }
      if (withParameters) {
        return withoutParameters ? ThreeState.UNSURE : ThreeState.YES;
      }
      if (withoutParameters) {
        return ThreeState.NO;
      }
    }
    return ThreeState.fromBoolean(myMethod.hasParameters());
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    boolean isAutoImportName = myContainingClass != null &&
                               JavaCodeStyleManager.getInstance(myMethod.getProject())
                                 .isStaticAutoImportName(myContainingClass.getQualifiedName() + "." + myMethod.getName());
    MemberLookupHelper helper = myHelper != null ? myHelper : new MemberLookupHelper(myMethod, myContainingClass, false, false);
    boolean showPackage = myHelper != null && (!myHelper.willBeImported() || isAutoImportName);
    PsiSubstitutor substitutor = myQualifierSubstitutor;
    String className = myContainingClass == null ? "???" : myContainingClass.getName();

    String name = myMethod.getName();
    String mainLookupString = name;
    if (myHelper != null && StringUtil.isNotEmpty(className)) {
      mainLookupString = className + "." + myPresentableTypeArgs + name;
    }
    MarkupText main = MarkupText.plainText(mainLookupString);
    if (JavaDeprecationUtils.isDeprecated(myMethod, null)) {
      main = main.highlightAll(MarkupText.Kind.STRIKEOUT);
    }

    final String qname = helper.getContainingClass() == null ? "" : helper.getContainingClass().getQualifiedName();
    String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
    String location = showPackage && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

    final String params = helper.isMergedOverloads() ? "(...)" :
                          PsiFormatUtil.formatMethod(
                            myMethod, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, 
                            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);

    main = main.concat(params, MarkupText.Kind.NORMAL);
    if (helper.willBeImported() && StringUtil.isNotEmpty(className)) {
      main = main.concat(JavaBundle.message("member.in.class", className) + location, MarkupText.Kind.GRAYED);
    } else {
      main = main.concat(location, MarkupText.Kind.GRAYED);
    }

    PsiType type = MemberLookupHelper.patchGetClass(myMethod, substitutor.substitute(myMethod.getReturnType()));
    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(main)
      .withMainIcon(() -> myMethod.getIcon(0))
      .withDetailText(JavaModCompletionUtils.typeMarkup(type));
    return presentation;
  }
}

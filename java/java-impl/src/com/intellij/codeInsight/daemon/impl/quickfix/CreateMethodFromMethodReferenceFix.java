// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiMethodReferenceUtil;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Creates a method or a constructor for an unresolved method reference. When there are several target classes,
 * it asks the user to choose one.
 */
public final class CreateMethodFromMethodReferenceFix extends PsiBasedModCommandAction<PsiMethodReferenceExpression> {
  public CreateMethodFromMethodReferenceFix(@NotNull PsiMethodReferenceExpression methodRef) {
    super(methodRef);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression ref) {
    PsiType functionalInterfaceType = getFunctionalExpressionType(ref);
    if (functionalInterfaceType == null || LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) == null) return null;
    String name = ref.getReferenceName();
    if (name == null) return null;
    boolean constructor = ref.isConstructor() && name.equals(JavaKeywords.NEW);
    if (!constructor && !PsiNameHelper.getInstance(context.project()).isIdentifier(name)) return null;
    if (getTargetClasses(ref, context.project()).isEmpty()) return null;
    return Presentation.of(constructor ? QuickFixBundle.message("create.constructor.from.new.text")
                                       : QuickFixBundle.message("create.method.from.usage.text", name));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression ref) {
    List<CreateInTargetClassAction> actions = ContainerUtil.map(
      getTargetClasses(ref, context.project()), targetClass -> new CreateInTargetClassAction(ref, targetClass));
    return ModCommand.chooseAction(QuickFixBundle.message("target.class.chooser.title"), actions);
  }

  private static @NotNull List<PsiClass> getTargetClasses(@NotNull PsiMethodReferenceExpression ref, @NotNull Project project) {
    boolean constructor = ref.isConstructor();
    return CreateFromUsageUtils.filterTargetClasses(
      CreateFromUsageUtils.getTargetClasses(ref, true, psiClass -> !constructor && BaseIntentionAction.canModify(psiClass)),
      project);
  }

  private static @Nullable PsiType getFunctionalExpressionType(@NotNull PsiMethodReferenceExpression ref) {
    PsiType functionalInterfaceType = ref.getFunctionalInterfaceType();
    if (functionalInterfaceType != null) return functionalInterfaceType;
    Ref<PsiType> type = new Ref<>();
    if (LambdaUtil.processParentOverloads(ref, (fType) -> type.set(fType))) {
      return type.get();
    }
    return null;
  }

  /**
   * Creates the method or the constructor in one target class.
   */
  private final class CreateInTargetClassAction extends PsiUpdateModCommandAction<PsiMethodReferenceExpression> {
    private final @NotNull SmartPsiElementPointer<PsiClass> myTargetClass;

    private CreateInTargetClassAction(@NotNull PsiMethodReferenceExpression ref, @NotNull PsiClass targetClass) {
      super(ref);
      myTargetClass = SmartPointerManager.createPointer(targetClass);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateMethodFromMethodReferenceFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression ref) {
      PsiClass targetClass = myTargetClass.getElement();
      if (targetClass == null) return null;
      return Presentation.of(ClassPresentationUtil.getNameForClass(targetClass, false)).withIcon(targetClass.getIcon(0));
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodReferenceExpression expression, @NotNull ModPsiUpdater updater) {
      PsiClass originalTarget = myTargetClass.getElement();
      if (originalTarget == null) return;
      PsiClass targetClass = updater.getWritable(originalTarget);
      String methodName = expression.getReferenceName();
      if (methodName == null) return;
      ExpectedSignature signature = ExpectedSignature.from(expression);
      if (signature == null) return;

      PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      PsiMember enclosingContext = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiField.class, PsiClassInitializer.class);
      PsiElement guesserContext = PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiMethod.class);

      Project project = context.project();
      JVMElementFactory elementFactory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
      if (elementFactory == null) elementFactory = JavaPsiFacade.getElementFactory(project);

      PsiMethod method;
      List<Pair<PsiExpression, PsiType>> arguments = signature.arguments();
      ExpectedTypeInfo[] expectedReturn = signature.expectedReturn();
      boolean shouldBeAbstract = false;
      if (expression.isConstructor()) {
        method = (PsiMethod)targetClass.add(elementFactory.createConstructor());
      }
      else {
        PsiMethod prototype = elementFactory.createMethodFromText("public <__TMP__> __TMP__ " + methodName + "(){}", null);
        method = CreateMethodFromUsageFix.addMethod(targetClass, parentClass, enclosingContext, prototype);

        PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult =
          PsiMethodReferenceUtil.getQualifierResolveResult(expression);
        boolean secondSearchPossible = PsiMethodReferenceUtil.isSecondSearchPossible(
          arguments.stream().map(p -> p.second).toArray(PsiType[]::new), qualifierResolveResult, expression);
        if (secondSearchPossible) {
          arguments = arguments.subList(1, arguments.size());
        }
        if (!arguments.isEmpty()) {
          // A generic prototype lets the inference compute the types of the parameters.
          String protoText =
            "public <RET," + IntStream.range(0, arguments.size()).mapToObj(n -> "ARG" + n).collect(Collectors.joining(",")) + "> RET " +
            methodName + "(" + IntStream.range(0, arguments.size()).mapToObj(n -> "ARG" + n + " arg" + n).collect(Collectors.joining(",")) +
            "){}";
          method = (PsiMethod)method.replace(elementFactory.createMethodFromText(protoText, null));
          ExpectedSignature inferred = ExpectedSignature.from(expression);
          if (inferred != null) {
            expectedReturn = inferred.expectedReturn();
            arguments = inferred.arguments();
            if (secondSearchPossible) {
              arguments = arguments.subList(1, arguments.size());
            }
          }
        }
        CreateFromUsageUtils.setupVisibility(parentClass, targetClass, method.getModifierList());
        for (PsiTypeParameter parameter : method.getTypeParameters()) {
          parameter.delete();
        }
        if (!secondSearchPossible && CreateFromUsageUtils.shouldCreateStaticMember(expression, targetClass)) {
          PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
        }
        else if (targetClass.isInterface()) {
          shouldBeAbstract = true;
          PsiCodeBlock body = method.getBody();
          assert body != null;
          body.delete();
        }
      }

      setupTemplate(project, targetClass, method, shouldBeAbstract, arguments, expectedReturn, guesserContext, expression,
                    updater);
    }
  }

  /**
   * Adds the template fields for the parameters and the return type, and fills the body. Every PSI change happens
   * before the fields go into the {@link ModTemplateBuilder}, because the builder writes into the document at once.
   */
  private static void setupTemplate(@NotNull Project project,
                                    @NotNull PsiClass targetClass,
                                    @NotNull PsiMethod method,
                                    boolean shouldBeAbstract,
                                    @NotNull List<Pair<PsiExpression, PsiType>> arguments,
                                    ExpectedTypeInfo @NotNull [] expectedReturn,
                                    @Nullable PsiElement context,
                                    @NotNull PsiMethodReferenceExpression originalRef,
                                    @NotNull ModPsiUpdater updater) {
    RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
    CreateFromUsageUtils.setupMethodParameters(method, fields, context, PsiSubstitutor.EMPTY, arguments);
    PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement != null) {
      new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), fields, PsiSubstitutor.EMPTY)
        .setupTypeElement(returnTypeElement, expectedReturn, context, targetClass);
    }

    SmartPsiElementPointer<PsiMethod> methodPointer = SmartPointerManager.createPointer(method);
    // The target class can be in another file. Move the caret first, so the updater tracks that file.
    updater.moveCaretTo(method);
    ModTemplateBuilder builder = updater.templateBuilder();
    fields.flushTo(builder);
    PsiDocumentManager.getInstance(project).commitDocument(updater.getDocument());
    PsiMethod liveMethod = methodPointer.getElement();
    if (liveMethod == null) return;

    if (shouldBeAbstract) {
      updater.moveCaretTo(liveMethod.getTextRange().getEndOffset());
    }
    else {
      setupBody(liveMethod, updater);
    }
    if (fields.isEmpty()) return;
    builder.finishAt(updater.getCaretOffset());
    if (!shouldBeAbstract) {
      SmartPsiElementPointer<PsiMethodReferenceExpression> ptr = SmartPointerManager.createPointer(originalRef);
      builder.onTemplateFinished(_ -> correctBody(ptr));
    }
  }

  /**
   * Fills the body of the method from the file template and puts the caret in it. A second call gives the same
   * result as the first one.
   */
  private static void setupBody(@NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    CreateFromUsageUtils.setupMethodBody(method, updater);
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      CreateFromUsageUtils.setupEditor(body, updater);
    }
  }

  /**
   * Fills the body again after the template ends, because the body depends on the return type which the user chose.
   */
  private static @NotNull ModCommand correctBody(@NotNull SmartPsiElementPointer<PsiMethodReferenceExpression> originalRef) {
    PsiMethodReferenceExpression ref = originalRef.getElement();
    if (ref == null || !(ref.resolve() instanceof PsiMethod method) || method.getBody() == null) return ModCommand.nop();
    return ModCommand.psiUpdate(method, (writableMethod, methodUpdater) -> setupBody(writableMethod, methodUpdater));
  }

  private record ExpectedSignature(ExpectedTypeInfo @NotNull [] expectedReturn, @NotNull List<Pair<PsiExpression, PsiType>> arguments) {
    /**
     * @return the signature which the functional interface of the method reference expects, or null when the
     * functional interface is unknown
     */
    private static @Nullable ExpectedSignature from(@NotNull PsiMethodReferenceExpression expression) {
      PsiType functionalInterfaceType = getFunctionalExpressionType(expression);
      PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
      if (interfaceMethod == null) return null;
      PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
      if (interfaceReturnType == null) return null;

      PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, classResolveResult);
      ExpectedTypeInfo[] expectedTypes =
        {new ExpectedTypeInfoImpl(interfaceReturnType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, interfaceReturnType, TailTypes.noneType(), null,
                                  ExpectedTypeInfoImpl.NULL)};
      PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
      List<Pair<PsiExpression, PsiType>> origArgs =
        ContainerUtil.map(parameters, parameter -> Pair.create(null, substitutor.substitute(parameter.getType())));
      return new ExpectedSignature(expectedTypes, origArgs);
    }
  }
}

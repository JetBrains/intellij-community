// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.*;
import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

import static com.intellij.codeInsight.generation.JavaOverrideImplementMemberChooser.*;

public final class OverrideImplementUtil extends OverrideImplementExploreUtil {
  private static final Logger LOG = Logger.getInstance(OverrideImplementUtil.class);
  public static final String IMPLEMENT_COMMAND_MARKER = "implement";

  private OverrideImplementUtil() { }

  static @NotNull List<MethodImplementor> getImplementors() {
    return MethodImplementor.EXTENSION_POINT_NAME.getExtensionList();
  }

  /**
   * generate methods (with bodies) corresponding to given method declaration
   *  there are maybe two method implementations for one declaration
   * (e.g. EJB' create() -> ejbCreate(), ejbPostCreate() )
   * @param aClass context for method implementations
   * @param method method to override or implement
   * @param toCopyJavaDoc true if copy JavaDoc from method declaration
   * @return list of method prototypes
   */
  public static @NotNull List<PsiMethod> overrideOrImplementMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method, boolean toCopyJavaDoc) throws IncorrectOperationException {
    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);
    PsiSubstitutor substitutor = aClass.isInheritor(containingClass, true)
                                 ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY)
                                 : PsiSubstitutor.EMPTY;
    return overrideOrImplementMethod(aClass, method, substitutor, toCopyJavaDoc,
                                     JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION);
  }

  public static boolean isInsertOverride(@NotNull PsiMethod superMethod, @NotNull PsiClass targetClass) {
    if (!JavaCodeStyleSettings.getInstance(targetClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION) {
      return false;
    }
    return canInsertOverride(superMethod, targetClass);
  }

  public static boolean canInsertOverride(@NotNull PsiMethod superMethod, @NotNull PsiClass targetClass) {
    if (superMethod.isConstructor() || superMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, targetClass)) {
      return false;
    }
    if (targetClass.isRecord() && superMethod.getParameterList().isEmpty()) {
      PsiMethod method = targetClass.findMethodBySignature(superMethod, false);
      if (method != null && !method.isPhysical()) return false;
    }
    if (PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, targetClass)) return true;
    PsiClass superClass = superMethod.getContainingClass();
    return superClass != null && !superClass.isInterface();
  }

  public static @NotNull List<PsiMethod> overrideOrImplementMethod(@NotNull PsiClass aClass,
                                                                   @NotNull PsiMethod method,
                                                                   @NotNull PsiSubstitutor substitutor,
                                                                   boolean toCopyJavaDoc,
                                                                   boolean insertOverrideIfPossible) throws IncorrectOperationException {
    if (!method.isValid() || !substitutor.isValid()) return Collections.emptyList();

    List<PsiMethod> results = new ArrayList<>();
    for (final MethodImplementor implementor : getImplementors()) {
      final PsiMethod[] prototypes = implementor.createImplementationPrototypes(aClass, method);
      for (PsiMethod prototype : prototypes) {
        implementor.createDecorator(aClass, method, toCopyJavaDoc, insertOverrideIfPossible).consume(prototype);
        results.add(prototype);
      }
    }

    if (results.isEmpty()) {
      PsiMethod method1 = GenerateMembersUtil.substituteGenericMethod(method, substitutor, aClass);

      PsiClass copyClass = copyClass(aClass);
      PsiMethod result = (PsiMethod)copyClass.addBefore(method1, copyClass.getRBrace());
      if (PsiUtil.isAnnotationMethod(result)) {
        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)result).getDefaultValue();
        if (defaultValue != null) {
          PsiElement defaultKeyword = defaultValue;
          while (!(defaultKeyword instanceof PsiKeyword) && defaultKeyword != null) {
            defaultKeyword = defaultKeyword.getPrevSibling();
          }
          if (defaultKeyword == null) defaultKeyword = defaultValue;
          defaultValue.getParent().deleteChildRange(defaultKeyword, defaultValue);
        }
      }
      Consumer<PsiMethod> decorator = createDefaultDecorator(aClass, method, toCopyJavaDoc, insertOverrideIfPossible);
      decorator.consume(result);
      results.add(result);
    }

    results.removeIf(m -> aClass.findMethodBySignature(m, false) != null);

    return results;
  }

  private static @NotNull PsiClass copyClass(@NotNull PsiClass aClass) {
    Object marker = new Object();
    PsiTreeUtil.mark(aClass, marker);
    PsiElement copy = aClass.getContainingFile().copy();
    PsiElement copyClass = PsiTreeUtil.releaseMark(copy, marker);
    LOG.assertTrue(copyClass != null);
    return (PsiClass)copyClass;
  }

  public static @NotNull Consumer<PsiMethod> createDefaultDecorator(@NotNull PsiClass aClass,
                                                                    @NotNull PsiMethod method,
                                                                    final boolean toCopyJavaDoc,
                                                                    final boolean insertOverrideIfPossible) {
    return result -> decorateMethod(aClass, method, toCopyJavaDoc, insertOverrideIfPossible, result);
  }

  public static @NotNull PsiMethod decorateMethod(@NotNull PsiClass aClass,
                                                  @NotNull PsiMethod method,
                                                  boolean toCopyJavaDoc,
                                                  boolean insertOverrideIfPossible,
                                                  @NotNull PsiMethod result) {
    PsiUtil.setModifierProperty(result, PsiModifier.ABSTRACT, aClass.isInterface() && method.hasModifierProperty(PsiModifier.ABSTRACT));
    PsiUtil.setModifierProperty(result, PsiModifier.NATIVE, false);

    if (method.isConstructor() && aClass.hasModifier(JvmModifier.FINAL) && method.hasModifier(JvmModifier.PROTECTED)) {
      result.getModifierList().setModifierProperty(PsiModifier.PROTECTED, false);
    }

    if (!toCopyJavaDoc){
      deleteDocComment(result);
    }

    //method type params are not allowed when overriding from raw type
    final PsiTypeParameterList list = result.getTypeParameterList();
    if (list != null) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        for (PsiClassType classType : aClass.getSuperTypes()) {
          if (InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(classType), containingClass, true) && classType.isRaw()) {
            list.replace(JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeParameterList());
            break;
          }
        }
      }
    }

    annotateOnOverrideImplement(result, aClass, method, insertOverrideIfPossible);

    if (JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).REPEAT_SYNCHRONIZED &&
        method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      result.getModifierList().setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }

    final PsiCodeBlock body = JavaPsiFacade.getElementFactory(method.getProject()).createCodeBlockFromText("{}", null);
    PsiCodeBlock oldBody = result.getBody();
    if (oldBody != null) {
      oldBody.replace(body);
    }
    else {
      result.add(body);
    }

    setupMethodBody(result, method, aClass);

    // probably, it's better to reformat the whole method - it can go from other style sources
    final Project project = method.getProject();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    CommonCodeStyleSettings javaSettings = CodeStyle.getLanguageSettings(aClass.getContainingFile(), JavaLanguage.INSTANCE);
    boolean keepBreaks = javaSettings.KEEP_LINE_BREAKS;
    javaSettings.KEEP_LINE_BREAKS = false;
    try {
      result = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      result = (PsiMethod)codeStyleManager.reformat(result);
    }
    finally {
      javaSettings.KEEP_LINE_BREAKS = keepBreaks;
    }
    return result;
  }

  public static void deleteDocComment(@NotNull PsiMethod result) {
    PsiDocComment comment = result.getDocComment();
    if (comment != null){
      comment.delete();
    }
  }

  public static void annotateOnOverrideImplement(@NotNull PsiMethod method, @NotNull PsiClass targetClass, @NotNull PsiMethod overridden) {
    annotateOnOverrideImplement(method, targetClass, overridden,
                                JavaCodeStyleSettings.getInstance(targetClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION);
  }

  static void annotateOnOverrideImplement(@NotNull PsiMethod method,
                                          @NotNull PsiClass targetClass,
                                          @NotNull PsiMethod overridden,
                                          boolean insertOverride) {
    if (insertOverride && canInsertOverride(overridden, targetClass)) {
      AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(Override.class.getName(), PsiNameValuePair.EMPTY_ARRAY, method.getModifierList());
    }
    OverrideImplementsAnnotationsHandler.repeatAnnotationsFromSource(overridden, targetClass, method);
    GenerateMembersUtil.sortModifiers(method, overridden);
  }

  public static @Unmodifiable @NotNull List<PsiGenerationInfo<PsiMethod>> overrideOrImplementMethods(@NotNull PsiClass aClass,
                                                                                                     @NotNull Collection<? extends PsiMethodMember> candidates,
                                                                                                     boolean toCopyJavaDoc,
                                                                                                     boolean toInsertAtOverride)
    throws IncorrectOperationException {
    List<CandidateInfo> candidateInfos = ContainerUtil.map(candidates, s -> new CandidateInfo(s.getElement(), s.getSubstitutor()));
    final List<PsiMethod> methods = overrideOrImplementMethodCandidates(aClass, candidateInfos, toCopyJavaDoc, toInsertAtOverride);
    return convert2GenerationInfos(methods);
  }

  public static @NotNull List<PsiMethod> overrideOrImplementMethodCandidates(@NotNull PsiClass aClass,
                                                                             @NotNull Collection<? extends CandidateInfo> candidates,
                                                                             boolean toCopyJavaDoc,
                                                                             boolean insertOverrideWherePossible) throws IncorrectOperationException {
    List<PsiMethod> result = new ArrayList<>();
    for (CandidateInfo candidateInfo : candidates) {
      PsiMethod method = (PsiMethod)candidateInfo.getElement();
      ProgressManager.progress(method.getName());
      result.addAll(overrideOrImplementMethod(aClass, method, candidateInfo.getSubstitutor(),
                                              toCopyJavaDoc, insertOverrideWherePossible));
    }
    return result;
  }

  public static @Unmodifiable @NotNull List<PsiGenerationInfo<PsiMethod>> convert2GenerationInfos(@NotNull Collection<? extends PsiMethod> methods) {
    return ContainerUtil.map(methods, s -> createGenerationInfo(s));
  }

  public static @NotNull PsiGenerationInfo<PsiMethod> createGenerationInfo(@NotNull PsiMethod s) {
    return createGenerationInfo(s, true);
  }

  private static @NotNull PsiGenerationInfo<PsiMethod> createGenerationInfo(@NotNull PsiMethod s, boolean mergeIfExists) {
    for (MethodImplementor implementor : getImplementors()) {
      final GenerationInfo info = implementor.createGenerationInfo(s, mergeIfExists);
      if (info instanceof PsiGenerationInfo) {
        @SuppressWarnings("unchecked") final PsiGenerationInfo<PsiMethod> psiGenerationInfo = (PsiGenerationInfo<PsiMethod>)info;
        return psiGenerationInfo;
      }
    }
    return new PsiGenerationInfo<>(s);
  }

  private static @NotNull String callSuper(@NotNull PsiMethod superMethod, @NotNull PsiMethod overriding, PsiClass targetClass) {
    return callSuper(superMethod, overriding, targetClass, true);
  }

  private static @NotNull String callSuper(@NotNull PsiMethod superMethod, @NotNull PsiMethod overriding, PsiClass targetClass, boolean prependReturn) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (prependReturn && !superMethod.isConstructor() && !PsiTypes.voidType().equals(superMethod.getReturnType())) {
      buffer.append("return ");
    }
    PsiClass aClass = superMethod.getContainingClass();
    if (aClass != null && aClass.isInterface()) {
      PsiClass superQualifier = getSuperQualifier(aClass, targetClass);
      if (superQualifier != null) {
        buffer.append(superQualifier.getName()).append(".");
      }
    }
    buffer.append("super");
    PsiParameter[] parameters = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()) {
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parameters.length; i++) {
      String name = parameters[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }

  private static PsiClass getSuperQualifier(PsiClass aClass, PsiClass targetClass) {
    if (targetClass != null) {
      if (targetClass.isInheritor(aClass, false)) {
        return aClass;
      }

      for (PsiClassType type : targetClass.getSuperTypes()) {
        PsiClass superClass = type.resolve();
        if (InheritanceUtil.isInheritorOrSelf(superClass, aClass, true)) {
          return superClass.isInterface() ? superClass : null;
        }
      }
    }
    return null;
  }

  public static void setupMethodBody(@NotNull PsiMethod result, @NotNull PsiMethod originalMethod, @NotNull PsiClass targetClass) throws IncorrectOperationException {
    boolean isAbstract = originalMethod.hasModifierProperty(PsiModifier.ABSTRACT);
    String templateName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    FileTemplate template = FileTemplateManager.getInstance(originalMethod.getProject()).getCodeTemplate(templateName);
    setupMethodBody(result, originalMethod, targetClass, template);
  }

  public static void setupMethodBody(@NotNull PsiMethod result,
                                     @NotNull PsiMethod originalMethod,
                                     @NotNull PsiClass targetClass,
                                     @NotNull FileTemplate template) throws IncorrectOperationException {
    if (targetClass.isInterface()) {
      if (isImplementInterfaceInJava8Interface(targetClass) || originalMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        PsiUtil.setModifierProperty(result, PsiModifier.DEFAULT, true);
      }
      else {
        final PsiCodeBlock body = result.getBody();
        if (body != null) body.delete();
      }
    }
    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
    PsiType returnType = result.getReturnType();
    if (returnType == null) {
      returnType = PsiTypes.voidType();
    }
    Properties properties = FileTemplateManager.getInstance(targetClass.getProject()).getDefaultProperties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType, true));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(originalMethod, result, targetClass));
    properties.setProperty(FileTemplate.ATTRIBUTE_PLAIN_CALL_SUPER, callSuper(originalMethod, result, targetClass, false));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, result);

    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), originalMethod.getProject());
    if (factory == null) factory = JavaPsiFacade.getElementFactory(originalMethod.getProject());
    @NonNls String methodText;
    Project project = result.getProject();
    try {
      methodText = "void foo () {\n" + template.getText(properties) + "\n}";
      methodText = FileTemplateUtil.indent(methodText, project, fileType);
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Failed to parse file template", (Throwable)e);
    }
    PsiMethod m;
    try {
      m = factory.createMethodFromText(methodText, originalMethod);
    }
    catch (IncorrectOperationException e) {
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showErrorDialog(JavaBundle.message("override.implement.broken.file.template.message"),
                                     JavaBundle.message("override.implement.broken.file.template.title")));
      return;
    }
    PsiCodeBlock oldBody = result.getBody();
    if (oldBody != null) {
      oldBody.replace(m.getBody());
    }
  }

  private static boolean isImplementInterfaceInJava8Interface(@NotNull PsiClass targetClass) {
    if (!PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, targetClass)){
      return false;
    }
    String commandName = CommandProcessor.getInstance().getCurrentCommandName();
    return commandName != null && StringUtil.containsIgnoreCase(commandName, IMPLEMENT_COMMAND_MARKER);
  }

  public static void chooseAndOverrideMethods(Project project, Editor editor, PsiClass aClass){
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
    chooseAndOverrideOrImplementMethods(project, editor, aClass, false);
  }

  public static void chooseAndImplementMethods(Project project, Editor editor, PsiClass aClass){
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
    chooseAndOverrideOrImplementMethods(project, editor, aClass, true);
  }

  public static void chooseAndOverrideOrImplementMethods(final Project project,
                                                         final Editor editor,
                                                         @NotNull PsiClass aClass,
                                                         final boolean toImplement) {
    NonBlockingReadAction<JavaOverrideImplementMemberChooserContainer> prepareChooserTask = ReadAction.nonBlocking(() -> {
        return DumbService.getInstance(project).withAlternativeResolveEnabled(() -> prepareChooser(aClass, toImplement));
      })
      .expireWhen(() -> !aClass.isValid());

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      showAndPerform(project, editor, aClass, toImplement, prepareChooserTask.executeSynchronously());
    } else {
      prepareChooserTask.finishOnUiThread(ModalityState.defaultModalityState(), container -> {
        showAndPerform(project, editor, aClass, toImplement, container);
      }).submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  public static void showAndPerform(@NotNull Project project,
                                @NotNull Editor editor,
                                @NotNull PsiClass aClass,
                                boolean toImplement,
                                @Nullable JavaOverrideImplementMemberChooserContainer container) {
    JavaOverrideImplementMemberChooser showedChooser = showJavaOverrideImplementChooser(container, editor, aClass);
    if (showedChooser == null) return;

    final List<PsiMethodMember> selectedElements = showedChooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;
    PsiUtilCore.ensureValid(aClass);
    final ThrowableRunnable<RuntimeException> performImplementOverrideRunnable =
      () -> DumbService.getInstance(project).withAlternativeResolveEnabled(
        () -> overrideOrImplementMethodsInRightPlace(editor, aClass, selectedElements, showedChooser.getOptions()));
    if (Registry.is("run.refactorings.under.progress")) {
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(aClass)) {
        return;
      }
      final String commandName = CommandProcessor.getInstance().getCurrentCommandName();
      String title = ObjectUtils.notNull(commandName, getChooserTitle(toImplement, false));
      Runnable runnable = () -> ApplicationManagerEx.getApplicationEx()
        .runWriteActionWithCancellableProgressInDispatchThread(title, project, null,
                                                               indicator -> performImplementOverrideRunnable.run());
      if (commandName == null) {
        CommandProcessor.getInstance().executeCommand(project, runnable, title, null);
      }
      else {
        runnable.run();
      }
    }
    else {
      WriteCommandAction.writeCommandAction(project, aClass.getContainingFile()).run(performImplementOverrideRunnable);
    }
  }

  public static @Nullable JavaOverrideImplementMemberChooserContainer prepareChooser(@NotNull PsiClass aClass, boolean toImplement) {
    PsiUtilCore.ensureValid(aClass);
    Collection<CandidateInfo> candidates = getMethodsToOverrideImplement(aClass, toImplement);
    Collection<CandidateInfo> secondary =
      toImplement || aClass.isInterface() ? new ArrayList<>() : getMethodsToOverrideImplement(aClass, true);
    return prepareJavaOverrideImplementChooser(aClass, toImplement, candidates, secondary);
  }


  /**
   * @deprecated use {@link OverrideImplementUtil#showJavaOverrideImplementChooser(Editor, PsiElement, boolean, Collection, Collection, java.util.function.Consumer)}
   */
  @Deprecated(forRemoval = true)
  public static @Nullable MemberChooser<PsiMethodMember> showOverrideImplementChooser(@NotNull Editor editor,
                                                                            @NotNull PsiElement aClass,
                                                                            final boolean toImplement,
                                                                            @NotNull Collection<CandidateInfo> candidates,
                                                                            @NotNull Collection<CandidateInfo> secondary) {
    return showJavaOverrideImplementChooser(editor, aClass, toImplement, candidates, secondary);
  }

  /**
   * Can be called on EDT thread.
   * It is used only for backward compatibility.
   * @deprecated use {@link OverrideImplementUtil#showJavaOverrideImplementChooser(Editor, PsiElement, boolean, Collection, Collection, java.util.function.Consumer)}
   */
  @Deprecated(forRemoval = true)
  public static @Nullable JavaOverrideImplementMemberChooser showJavaOverrideImplementChooser(@NotNull Editor editor,
                                                                                    @NotNull PsiElement aClass,
                                                                                    final boolean toImplement,
                                                                                    @NotNull Collection<CandidateInfo> candidates,
                                                                                    @NotNull Collection<CandidateInfo> secondary) {
    final JavaOverrideImplementMemberChooserContainer container =
      prepareJavaOverrideImplementChooser(aClass, toImplement, candidates, secondary);
    if (container == null) {
      return null;
    }
    return showJavaOverrideImplementChooser(container, editor, aClass);
  }

  /**
   * Must be call on a background thread
   * @param candidates, secondary should allow modifications
   */
  public static void showJavaOverrideImplementChooser(@NotNull Editor editor,
                                                      @NotNull PsiElement aClass,
                                                      final boolean toImplement,
                                                      @NotNull Collection<CandidateInfo> candidates,
                                                      @NotNull Collection<CandidateInfo> secondary,
                                                      @NotNull java.util.function.Consumer<JavaOverrideImplementMemberChooser> callback) {
    ReadAction.nonBlocking(() -> {
        return prepareJavaOverrideImplementChooser(aClass, toImplement, candidates, secondary);
      })
      .finishOnUiThread(ModalityState.defaultModalityState(), container -> {
        JavaOverrideImplementMemberChooser chooser = showJavaOverrideImplementChooser(container, editor, aClass);
        callback.accept(chooser);
      })
      .expireWhen(() -> !aClass.isValid())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  /**
   * Must be called on EDT thread
   */
  private static @Nullable JavaOverrideImplementMemberChooser showJavaOverrideImplementChooser(@Nullable JavaOverrideImplementMemberChooserContainer container,
                                                                                     @NotNull Editor editor,
                                                                                     @NotNull PsiElement aClass) {
    if (container == null) {
      return null;
    }
    final JavaOverrideImplementMemberChooser chooser = create(container);
    Project project = aClass.getProject();
    registerHandlerForComplementaryAction(project, editor, aClass, container.toImplement(), chooser);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return chooser;
    }
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

    return chooser;
  }

  private static @Nullable JavaOverrideImplementMemberChooserContainer prepareJavaOverrideImplementChooser(@NotNull PsiElement aClass,
                                                                                                           boolean toImplement,
                                                                                                           @NotNull Collection<CandidateInfo> candidates,
                                                                                                           @NotNull Collection<CandidateInfo> secondary) {
    Computable<JavaOverrideImplementMemberChooserContainer> computable = () -> {
      if (toImplement) {
        for (Iterator<CandidateInfo> iterator = candidates.iterator(); iterator.hasNext(); ) {
          CandidateInfo candidate = iterator.next();
          PsiElement element = candidate.getElement();
          if (element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.DEFAULT)) {
            iterator.remove();
            secondary.add(candidate);
          }
        }
      }

      return prepare(aClass, toImplement, candidates, secondary);
    };
    if (EDT.isCurrentThreadEdt()) {
      return ActionUtil.underModalProgress(aClass.getProject(),
                                           CodeInsightBundle.message("progress.title.resolving.reference"), computable);
    }
    else {
      return computable.get();
    }
  }

  private static void registerHandlerForComplementaryAction(@NotNull Project project,
                                                            @NotNull Editor editor,
                                                            @NotNull PsiElement aClass,
                                                            final boolean toImplement,
                                                            @NotNull MemberChooser<PsiMethodMember> chooser) {
    final JComponent preferredFocusedComponent = chooser.getPreferredFocusedComponent();

    final @NonNls String s = toImplement ? "OverrideMethods" : "ImplementMethods";
    final Shortcut shortcut = KeymapUtil.getPrimaryShortcut(s);

    if (shortcut instanceof KeyboardShortcut) {
      preferredFocusedComponent.getInputMap().put(
        ((KeyboardShortcut)shortcut).getFirstKeyStroke(), s
      );

      preferredFocusedComponent.getActionMap().put(
          s,
          new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
              chooser.close(DialogWrapper.CANCEL_EXIT_CODE);

              // invoke later in order to close previous modal dialog
              ApplicationManager.getApplication().invokeLater(() -> {
                CodeInsightActionHandler handler = toImplement ? new OverrideMethodsHandler(): new ImplementMethodsHandler();
                handler.invoke(project, editor, aClass.getContainingFile());
              }, project.getDisposed());
            }
          }
      );
    }
  }

  public static void overrideOrImplementMethodsInRightPlace(@NotNull Editor editor,
                                                            @NotNull PsiClass aClass,
                                                            @NotNull Collection<? extends PsiMethodMember> members,
                                                            boolean copyJavadoc) {
    boolean insert = JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;
    overrideOrImplementMethodsInRightPlace(editor, aClass, members, copyJavadoc, insert);
  }

  public static void overrideOrImplementMethodsInRightPlace(@NotNull Editor editor,
                                                            @NotNull PsiClass aClass,
                                                            @NotNull Collection<? extends PsiMethodMember> candidates,
                                                            boolean copyJavadoc,
                                                            boolean insertOverrideWherePossible) {
    OverrideOrImplementOptions options = new OverrideOrImplementOptions()
      .copyJavadoc(copyJavadoc)
      .insertOverrideWherePossible(insertOverrideWherePossible);
    overrideOrImplementMethodsInRightPlace(editor, aClass, candidates, options);
  }

  public static void overrideOrImplementMethodsInRightPlace(@NotNull Editor editor,
                                                            @NotNull PsiClass aClass,
                                                            @NotNull Collection<? extends PsiMethodMember> candidates,
                                                            OverrideOrImplementOptions options) {
    try {
      int offset = editor.getCaretModel().getOffset();
      PsiElement brace = aClass.getLBrace();
      if (brace == null && !(aClass instanceof PsiImplicitClass)) {
        PsiClass psiClass = JavaPsiFacade.getElementFactory(aClass.getProject()).createClass("X");
        brace = aClass.addRangeAfter(psiClass.getLBrace(), psiClass.getRBrace(), aClass.getLastChild());
        LOG.assertTrue(brace != null, aClass.getLastChild());
      }

      int lbraceOffset = brace == null ? 0 : brace.getTextOffset();
      List<PsiGenerationInfo<PsiMethod>> resultMembers;
      if (offset <= lbraceOffset || aClass.isEnum()) {
        resultMembers = new ArrayList<>();
        for (PsiMethodMember candidate : candidates) {
          ProgressManager.progress(candidate.getElement().getName());
          Collection<PsiMethod> prototypes = overrideOrImplementMethod(aClass, candidate.getElement(), candidate.getSubstitutor(),
                                                                       options.isCopyJavaDoc(), options.isInsertOverrideWherePossible());
          List<PsiGenerationInfo<PsiMethod>> infos = convert2GenerationInfos(prototypes);
          for (PsiGenerationInfo<PsiMethod> info : infos) {
            PsiElement anchor = getDefaultAnchorToOverrideOrImplement(aClass, candidate.getElement(), candidate.getSubstitutor());
            info.insert(aClass, anchor, true);
            resultMembers.add(info);
          }
        }
      }
      else {
        List<PsiGenerationInfo<PsiMethod>> prototypes = overrideOrImplementMethods(aClass, candidates, options.isCopyJavaDoc(),
                                                                                   options.isInsertOverrideWherePossible());
        resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass, offset, prototypes);
      }

      if (!resultMembers.isEmpty()) {
        for (PsiGenerationInfo<PsiMethod> info : resultMembers) {
          PsiMethod method = info.getPsiMember();
          if (options.isGenerateJavaDoc() && method != null && method.getDocComment() == null) {
            method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
            if (method != null) {
              FixDocCommentAction.generateOrFixComment(method, method.getProject(), editor);
            }
          }
        }
        resultMembers.get(0).positionCaret(editor, true);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static @Nullable PsiElement getDefaultAnchorToOverrideOrImplement(@NotNull PsiClass aClass, @NotNull PsiMethod baseMethod, @NotNull PsiSubstitutor substitutor){
    PsiMethod prevBaseMethod = PsiTreeUtil.getPrevSiblingOfType(baseMethod, PsiMethod.class);
    while(prevBaseMethod != null) {
      String name = prevBaseMethod.isConstructor() ? aClass.getName() : prevBaseMethod.getName();
      //Happens when aClass instanceof PsiAnonymousClass
      if (name != null) {
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, prevBaseMethod.getParameterList(), prevBaseMethod.getTypeParameterList(), substitutor, prevBaseMethod.isConstructor());
        PsiMethod prevMethod = MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
        if (prevMethod != null && prevMethod.isPhysical()){
          return prevMethod.getNextSibling();
        }
      }
      prevBaseMethod = PsiTreeUtil.getPrevSiblingOfType(prevBaseMethod, PsiMethod.class);
    }

    PsiMethod nextBaseMethod = PsiTreeUtil.getNextSiblingOfType(baseMethod, PsiMethod.class);
    while(nextBaseMethod != null) {
      String name = nextBaseMethod.isConstructor() ? aClass.getName() : nextBaseMethod.getName();
      if (name != null) {
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, nextBaseMethod.getParameterList(), nextBaseMethod.getTypeParameterList(), substitutor, nextBaseMethod.isConstructor());
        PsiMethod nextMethod = MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
        if (nextMethod != null && nextMethod.isPhysical()){
          return nextMethod;
        }
      }
      nextBaseMethod = PsiTreeUtil.getNextSiblingOfType(nextBaseMethod, PsiMethod.class);
    }

    return null;
  }

  public static @NotNull List<PsiGenerationInfo<PsiMethod>> overrideOrImplement(@NotNull PsiClass psiClass, @NotNull PsiMethod baseMethod) throws IncorrectOperationException {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(baseMethod.getProject());
    List<PsiGenerationInfo<PsiMethod>> results = new ArrayList<>();
    try {
      List<PsiGenerationInfo<PsiMethod>> prototypes = convert2GenerationInfos(overrideOrImplementMethod(psiClass, baseMethod, false));
      if (prototypes.isEmpty()) return Collections.emptyList();

      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseMethod.getContainingClass(), psiClass, PsiSubstitutor.EMPTY);
      PsiElement anchor = getDefaultAnchorToOverrideOrImplement(psiClass, baseMethod, substitutor);
      results = GenerateMembersUtil.insertMembersBeforeAnchor(psiClass, anchor, prototypes);

      return results;
    }
    finally {
      PsiFile psiFile = psiClass.getContainingFile();
      if (psiFile.isPhysical()) {
        Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), true);
        if (editor != null && !results.isEmpty()) {
          results.get(0).positionCaret(editor, true);
          editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        }
      }
    }
  }

  public static @Nullable PsiClass getContextClass(Project project, @NotNull Editor editor, @NotNull PsiFile file, boolean allowInterface) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass)element;
    if (aClass instanceof PsiSyntheticClass) return null;
    if (file instanceof PsiJavaFile javaFile) {
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass implicitClass &&
          implicitClass.getFirstChild() != null && PsiMethodUtil.hasMainMethod(implicitClass)) {
        return classes[0];
      }
    }
    return aClass == null || !allowInterface && aClass.isInterface() ? null : aClass;
  }


  public static @NotNull List<PsiMethod> overrideOrImplementMethodCandidates(@NotNull PsiClass aClass, @NotNull Collection<? extends CandidateInfo> candidatesToImplement,
                                                                             boolean copyJavadoc) throws IncorrectOperationException {
    boolean insert =
      JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;
    return overrideOrImplementMethodCandidates(aClass, candidatesToImplement, copyJavadoc, insert);
  }
}
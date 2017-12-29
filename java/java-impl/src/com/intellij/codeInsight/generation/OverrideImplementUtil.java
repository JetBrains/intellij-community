// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

public class OverrideImplementUtil extends OverrideImplementExploreUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.OverrideImplementUtil");
  public static final String IMPLEMENT_COMMAND_MARKER = "implement";

  private OverrideImplementUtil() { }

  protected static MethodImplementor[] getImplementors() {
    return Extensions.getExtensions(MethodImplementor.EXTENSION_POINT_NAME);
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
  @NotNull
  public static List<PsiMethod> overrideOrImplementMethod(PsiClass aClass, PsiMethod method, boolean toCopyJavaDoc) throws IncorrectOperationException {
    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);
    PsiSubstitutor substitutor = aClass.isInheritor(containingClass, true)
                                 ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY)
                                 : PsiSubstitutor.EMPTY;
    return overrideOrImplementMethod(aClass, method, substitutor, toCopyJavaDoc,
                                     CodeStyleSettingsManager.getSettings(aClass.getProject())
                                       .getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION);
  }

  public static boolean isInsertOverride(PsiMethod superMethod, PsiClass targetClass) {
    if (!CodeStyleSettingsManager.getSettings(targetClass.getProject()).getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION) {
      return false;
    }
    return canInsertOverride(superMethod, targetClass);
  }

  public static boolean canInsertOverride(PsiMethod superMethod, PsiClass targetClass) {
    if (superMethod.isConstructor() || superMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (!PsiUtil.isLanguageLevel5OrHigher(targetClass)) {
      return false;
    }
    if (PsiUtil.isLanguageLevel6OrHigher(targetClass)) return true;
    PsiClass superClass = superMethod.getContainingClass();
    return superClass != null && !superClass.isInterface();
  }

  public static List<PsiMethod> overrideOrImplementMethod(PsiClass aClass,
                                                          PsiMethod method,
                                                          PsiSubstitutor substitutor,
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

      PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
      PsiMethod result = (PsiMethod)factory.createClass("Dummy").add(method1);
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

    for (Iterator<PsiMethod> iterator = results.iterator(); iterator.hasNext();) {
      if (aClass.findMethodBySignature(iterator.next(), false) != null) {
        iterator.remove();
      }
    }

    return results;
  }

  public static Consumer<PsiMethod> createDefaultDecorator(final PsiClass aClass,
                                                           final PsiMethod method,
                                                           final boolean toCopyJavaDoc,
                                                           final boolean insertOverrideIfPossible) {
    return result -> decorateMethod(aClass, method, toCopyJavaDoc, insertOverrideIfPossible, result);
  }

  private static PsiMethod decorateMethod(PsiClass aClass,
                                          PsiMethod method,
                                          boolean toCopyJavaDoc,
                                          boolean insertOverrideIfPossible,
                                          PsiMethod result) {
    PsiUtil.setModifierProperty(result, PsiModifier.ABSTRACT, aClass.isInterface() && method.hasModifierProperty(PsiModifier.ABSTRACT));
    PsiUtil.setModifierProperty(result, PsiModifier.NATIVE, false);

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

    if (CodeStyleSettingsManager.getSettings(aClass.getProject()).getCustomSettings(JavaCodeStyleSettings.class).REPEAT_SYNCHRONIZED &&
        method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      result.getModifierList().setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }

    final PsiCodeBlock body = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createCodeBlockFromText("{}", null);
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
    CommonCodeStyleSettings javaSettings = CodeStyleSettingsManager.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE);
    boolean keepBreaks = javaSettings.KEEP_LINE_BREAKS;
    javaSettings.KEEP_LINE_BREAKS = false;
    result = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    result = (PsiMethod)codeStyleManager.reformat(result);
    javaSettings.KEEP_LINE_BREAKS = keepBreaks;
    return result;
  }

  public static void deleteDocComment(PsiMethod result) {
    PsiDocComment comment = result.getDocComment();
    if (comment != null){
      comment.delete();
    }
  }

  public static void annotateOnOverrideImplement(PsiMethod method, PsiClass targetClass, PsiMethod overridden) {
    annotateOnOverrideImplement(method, targetClass, overridden,
                                CodeStyleSettingsManager.getSettings(method.getProject())
                                  .getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION);
  }

  public static void annotateOnOverrideImplement(PsiMethod method, PsiClass targetClass, PsiMethod overridden, boolean insertOverride) {
    if (insertOverride && canInsertOverride(overridden, targetClass)) {
      final String overrideAnnotationName = Override.class.getName();
      if (!AnnotationUtil.isAnnotated(method, overrideAnnotationName, 0)) {
        AddAnnotationPsiFix.addPhysicalAnnotation(overrideAnnotationName, PsiNameValuePair.EMPTY_ARRAY, method.getModifierList());
      }
    }
    OverrideImplementsAnnotationsHandler.repeatAnnotationsFromSource(overridden, targetClass, method);
  }

  public static void annotate(@NotNull PsiMethod result, String fqn, String... annosToRemove) throws IncorrectOperationException {
    Project project = result.getProject();
    AddAnnotationFix fix = new AddAnnotationFix(fqn, result, annosToRemove);
    if (fix.isAvailable(project, null, result.getContainingFile())) {
      fix.invoke(project, null, result.getContainingFile());
    }
  }

  @NotNull
  public static List<PsiGenerationInfo<PsiMethod>> overrideOrImplementMethods(PsiClass aClass,
                                                                              Collection<PsiMethodMember> candidates,
                                                                              boolean toCopyJavaDoc,
                                                                              boolean toInsertAtOverride)
    throws IncorrectOperationException {
    List<CandidateInfo> candidateInfos = ContainerUtil.map2List(candidates, s -> new CandidateInfo(s.getElement(), s.getSubstitutor()));
    final List<PsiMethod> methods = overrideOrImplementMethodCandidates(aClass, candidateInfos, toCopyJavaDoc, toInsertAtOverride);
    return convert2GenerationInfos(methods);
  }

  @NotNull
  public static List<PsiMethod> overrideOrImplementMethodCandidates(PsiClass aClass,
                                                                    Collection<CandidateInfo> candidates,
                                                                    boolean toCopyJavaDoc,
                                                                    boolean insertOverrideWherePossible) throws IncorrectOperationException {
    List<PsiMethod> result = new ArrayList<>();
    for (CandidateInfo candidateInfo : candidates) {
      result.addAll(overrideOrImplementMethod(aClass, (PsiMethod)candidateInfo.getElement(), candidateInfo.getSubstitutor(),
                                              toCopyJavaDoc, insertOverrideWherePossible));
    }
    return result;
  }

  public static List<PsiGenerationInfo<PsiMethod>> convert2GenerationInfos(final Collection<PsiMethod> methods) {
    return ContainerUtil.map2List(methods, s -> createGenerationInfo(s));
  }

  public static PsiGenerationInfo<PsiMethod> createGenerationInfo(PsiMethod s) {
    return createGenerationInfo(s, true);
  }

  public static PsiGenerationInfo<PsiMethod> createGenerationInfo(PsiMethod s, boolean mergeIfExists) {
    for (MethodImplementor implementor : getImplementors()) {
      final GenerationInfo info = implementor.createGenerationInfo(s, mergeIfExists);
      if (info instanceof PsiGenerationInfo) {
        @SuppressWarnings({"unchecked"}) final PsiGenerationInfo<PsiMethod> psiGenerationInfo = (PsiGenerationInfo<PsiMethod>)info;
        return psiGenerationInfo;
      }
    }
    return new PsiGenerationInfo<>(s);
  }

  @NotNull
  public static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    return callSuper(superMethod, overriding, true);
  }

  @NotNull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding, boolean prependReturn) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (prependReturn && !superMethod.isConstructor() && !PsiType.VOID.equals(superMethod.getReturnType())) {
      buffer.append("return ");
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

  public static void setupMethodBody(PsiMethod result, PsiMethod originalMethod, PsiClass targetClass) throws IncorrectOperationException {
    boolean isAbstract = originalMethod.hasModifierProperty(PsiModifier.ABSTRACT) || originalMethod.hasModifierProperty(PsiModifier.DEFAULT);
    String templateName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    FileTemplate template = FileTemplateManager.getInstance(originalMethod.getProject()).getCodeTemplate(templateName);
    setupMethodBody(result, originalMethod, targetClass, template);
  }

  public static void setupMethodBody(final PsiMethod result,
                                     final PsiMethod originalMethod,
                                     final PsiClass targetClass,
                                     final FileTemplate template) throws IncorrectOperationException {
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
      returnType = PsiType.VOID;
    }
    Properties properties = FileTemplateManager.getInstance(targetClass.getProject()).getDefaultProperties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType, true));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(originalMethod, result));
    properties.setProperty(FileTemplate.ATTRIBUTE_PLAIN_CALL_SUPER, callSuper(originalMethod, result, false));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, result);

    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), originalMethod.getProject());
    if (factory == null) factory = JavaPsiFacade.getInstance(originalMethod.getProject()).getElementFactory();
    @NonNls String methodText;

    try {
      methodText = "void foo () {\n" + template.getText(properties) + "\n}";
      methodText = FileTemplateUtil.indent(methodText, result.getProject(), fileType);
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Failed to parse file template", (Throwable)e);
    }
    if (methodText != null) {
      PsiMethod m;
      try {
        m = factory.createMethodFromText(methodText, originalMethod);
      }
      catch (IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showErrorDialog(CodeInsightBundle.message("override.implement.broken.file.template.message"),
                                       CodeInsightBundle.message("override.implement.broken.file.template.title")));
        return;
      }
      PsiCodeBlock oldBody = result.getBody();
      if (oldBody != null) {
        oldBody.replace(m.getBody());
      }
    }
  }

  private static boolean isImplementInterfaceInJava8Interface(PsiClass targetClass) {
    if (!PsiUtil.isLanguageLevel8OrHigher(targetClass)){
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
                                                         final PsiClass aClass,
                                                         final boolean toImplement) {
    LOG.assertTrue(aClass.isValid());
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Collection<CandidateInfo> candidates = getMethodsToOverrideImplement(aClass, toImplement);
    Collection<CandidateInfo> secondary = toImplement || aClass.isInterface() ?
                                          ContainerUtil.newArrayList() : getMethodsToOverrideImplement(aClass, true);

    final MemberChooser<PsiMethodMember> chooser = showOverrideImplementChooser(editor, aClass, toImplement, candidates, secondary);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;

    LOG.assertTrue(aClass.isValid());
    new WriteCommandAction(project, aClass.getContainingFile()) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        overrideOrImplementMethodsInRightPlace(editor, aClass, selectedElements, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());
      }
    }.execute();
  }

  /**
   * @param candidates, secondary should allow modifications
   */
  @Nullable
  public static MemberChooser<PsiMethodMember> showOverrideImplementChooser(Editor editor,
                                                                            final PsiElement aClass,
                                                                            final boolean toImplement,
                                                                            final Collection<CandidateInfo> candidates,
                                                                            Collection<CandidateInfo> secondary) {

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

    final JavaOverrideImplementMemberChooser chooser =
      JavaOverrideImplementMemberChooser.create(aClass, toImplement, candidates, secondary);
    if (chooser == null) {
      return null;
    }
    Project project = aClass.getProject();
    registerHandlerForComplementaryAction(project, editor, aClass, toImplement, chooser);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return chooser;
    }
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

    return chooser;
  }

  private static void registerHandlerForComplementaryAction(final Project project,
                                                            final Editor editor,
                                                            final PsiElement aClass,
                                                            final boolean toImplement,
                                                            final MemberChooser<PsiMethodMember> chooser) {
    final JComponent preferredFocusedComponent = chooser.getPreferredFocusedComponent();
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();

    @NonNls final String s = toImplement ? "OverrideMethods" : "ImplementMethods";
    final Shortcut[] shortcuts = keymap.getShortcuts(s);

    if (shortcuts.length > 0 && shortcuts[0] instanceof KeyboardShortcut) {
      preferredFocusedComponent.getInputMap().put(
        ((KeyboardShortcut)shortcuts[0]).getFirstKeyStroke(), s
      );

      preferredFocusedComponent.getActionMap().put(
          s,
          new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
              chooser.close(DialogWrapper.CANCEL_EXIT_CODE);

              // invoke later in order to close previous modal dialog
              TransactionGuard.getInstance().submitTransactionLater(project, () -> {
                CodeInsightActionHandler handler = toImplement ? new OverrideMethodsHandler(): new ImplementMethodsHandler();
                handler.invoke(project, editor, aClass.getContainingFile());
              });
            }
          }
      );
    }
  }

  public static void overrideOrImplementMethodsInRightPlace(Editor editor,
                                                            PsiClass aClass,
                                                            Collection<PsiMethodMember> candidates,
                                                            boolean copyJavadoc,
                                                            boolean insertOverrideWherePossible) {
    try {
      int offset = editor.getCaretModel().getOffset();
      PsiElement brace = aClass.getLBrace();
      if (brace == null) {
        PsiClass psiClass = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createClass("X");
        brace = aClass.addRangeAfter(psiClass.getLBrace(), psiClass.getRBrace(), aClass.getLastChild());
        LOG.assertTrue(brace != null, aClass.getLastChild());
      }

      int lbraceOffset = brace.getTextOffset();
      List<PsiGenerationInfo<PsiMethod>> resultMembers;
      if (offset <= lbraceOffset || aClass.isEnum()) {
        resultMembers = new ArrayList<>();
        for (PsiMethodMember candidate : candidates) {
          Collection<PsiMethod> prototypes =
            overrideOrImplementMethod(aClass, candidate.getElement(), candidate.getSubstitutor(), copyJavadoc, insertOverrideWherePossible);
          List<PsiGenerationInfo<PsiMethod>> infos = convert2GenerationInfos(prototypes);
          for (PsiGenerationInfo<PsiMethod> info : infos) {
            PsiElement anchor = getDefaultAnchorToOverrideOrImplement(aClass, candidate.getElement(), candidate.getSubstitutor());
            info.insert(aClass, anchor, true);
            resultMembers.add(info);
          }
        }
      }
      else {
        List<PsiGenerationInfo<PsiMethod>> prototypes = overrideOrImplementMethods(aClass, candidates, copyJavadoc, insertOverrideWherePossible);
        resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass, offset, prototypes);
      }

      if (!resultMembers.isEmpty()) {
        resultMembers.get(0).positionCaret(editor, true);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public static PsiElement getDefaultAnchorToOverrideOrImplement(PsiClass aClass, PsiMethod baseMethod, PsiSubstitutor substitutor){
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

  public static List<PsiGenerationInfo<PsiMethod>> overrideOrImplement(PsiClass psiClass, @NotNull PsiMethod baseMethod) throws IncorrectOperationException {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(baseMethod.getProject());
    List<PsiGenerationInfo<PsiMethod>> results = new ArrayList<>();
    try {

      List<PsiGenerationInfo<PsiMethod>> prototypes = convert2GenerationInfos(overrideOrImplementMethod(psiClass, baseMethod, false));
      if (prototypes.isEmpty()) return null;

      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseMethod.getContainingClass(), psiClass, PsiSubstitutor.EMPTY);
      PsiElement anchor = getDefaultAnchorToOverrideOrImplement(psiClass, baseMethod, substitutor);
      results = GenerateMembersUtil.insertMembersBeforeAnchor(psiClass, anchor, prototypes);

      return results;
    }
    finally {

      PsiFile psiFile = psiClass.getContainingFile();
      Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), true);
      if (editor != null && !results.isEmpty()) {
        results.get(0).positionCaret(editor, true);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
    }
  }

  @Nullable
  public static PsiClass getContextClass(Project project, Editor editor, PsiFile file, boolean allowInterface) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass)element;
    if (aClass instanceof PsiSyntheticClass) return null;
    return aClass == null || !allowInterface && aClass.isInterface() ? null : aClass;
  }

  public static void overrideOrImplementMethodsInRightPlace(Editor editor1, PsiClass aClass, Collection<PsiMethodMember> members, boolean copyJavadoc) {
    boolean insert =
      CodeStyleSettingsManager.getSettings(aClass.getProject()).getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION;
    overrideOrImplementMethodsInRightPlace(editor1, aClass, members, copyJavadoc, insert);
  }

  public static List<PsiMethod> overrideOrImplementMethodCandidates(PsiClass aClass, Collection<CandidateInfo> candidatesToImplement,
                                                                    boolean copyJavadoc) throws IncorrectOperationException {
    boolean insert =
      CodeStyleSettingsManager.getSettings(aClass.getProject()).getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION;
    return overrideOrImplementMethodCandidates(aClass, candidatesToImplement, copyJavadoc, insert);
  }
}
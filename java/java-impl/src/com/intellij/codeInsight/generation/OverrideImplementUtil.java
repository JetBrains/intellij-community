/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class OverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.OverrideImplementUtil");

  @NonNls private static final String PROP_COMBINED_OVERRIDE_IMPLEMENT = "OverrideImplement.combined";

  private OverrideImplementUtil() {
  }

  @NotNull
  public static Collection<CandidateInfo> getMethodsToOverrideImplement(PsiClass aClass, boolean toImplement) {
    return getMapToOverrideImplement(aClass, toImplement).values();
  }

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToImplement(@NotNull PsiClass aClass) {
    return getMapToOverrideImplement(aClass, true).keySet();
  }

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToOverride(@NotNull PsiClass aClass) {
    return getMapToOverrideImplement(aClass, false).keySet();
  }

  @NotNull
  private static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(PsiClass aClass, boolean toImplement) {
    Map<MethodSignature, PsiMethod> abstracts = new LinkedHashMap<MethodSignature,PsiMethod>();
    Map<MethodSignature, PsiMethod> finals = new LinkedHashMap<MethodSignature,PsiMethod>();
    Map<MethodSignature, PsiMethod> concretes = new LinkedHashMap<MethodSignature,PsiMethod>();

    LOG.assertTrue(aClass.isValid());
    Collection<HierarchicalMethodSignature> allMethodSigs = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (HierarchicalMethodSignature signature : allMethodSigs) {
      PsiMethod method = signature.getMethod();
      LOG.assertTrue(method.isValid(), aClass);

      if (method.hasModifierProperty(PsiModifier.STATIC) || !resolveHelper.isAccessible(method, aClass, aClass)) continue;
      PsiClass hisClass = method.getContainingClass();
      if (hisClass == null) continue;
      // filter non-immediate super constructors
      if (method.isConstructor() && (!aClass.isInheritor(hisClass, false) || aClass instanceof PsiAnonymousClass || aClass.isEnum())) {
        continue;
      }
      // filter already implemented
      if (MethodSignatureUtil.findMethodBySignature(aClass, signature, false) != null) {
        continue;
      }

      if (method.hasModifierProperty(PsiModifier.FINAL)) {
        finals.put(signature, method);
        continue;
      }

      Map<MethodSignature, PsiMethod> map = hisClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT) ? abstracts : concretes;
      PsiMethod other = map.get(signature);
      if (other == null || preferLeftForImplement(method, other)) {
        map.put(signature, method);
      }
    }

    final Map<MethodSignature, CandidateInfo> result = new TreeMap<MethodSignature,CandidateInfo>(new MethodSignatureComparator());
    if (toImplement || aClass.isInterface()) {
      collectMethodsToImplement(aClass, abstracts, finals, concretes, result);
    }
    else {
      for (Map.Entry<MethodSignature, PsiMethod> entry : concretes.entrySet()) {
        MethodSignature signature = entry.getKey();
        PsiMethod concrete = entry.getValue();
        if (finals.get(signature) == null) {
          PsiMethod abstractOne = abstracts.get(signature);
          if (abstractOne == null || !abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true) ||
              CommonClassNames.JAVA_LANG_OBJECT.equals(concrete.getContainingClass().getQualifiedName())) {
            PsiSubstitutor subst = GenerateMembersUtil.correctSubstitutor(concrete, signature.getSubstitutor());
            CandidateInfo info = new CandidateInfo(concrete, subst);
            result.put(signature, info);
          }
        }
      }
    }

    return result;
  }

  public static void collectMethodsToImplement(PsiClass aClass,
                                               Map<MethodSignature, PsiMethod> abstracts,
                                               Map<MethodSignature, PsiMethod> finals,
                                               Map<MethodSignature, PsiMethod> concretes,
                                               Map<MethodSignature, CandidateInfo> result) {
    for (Map.Entry<MethodSignature, PsiMethod> entry : abstracts.entrySet()) {
      MethodSignature signature = entry.getKey();
      PsiMethod abstractOne = entry.getValue();
      PsiMethod concrete = concretes.get(signature);
      if (concrete == null
          || PsiUtil.getAccessLevel(concrete.getModifierList()) < PsiUtil.getAccessLevel(abstractOne.getModifierList())
          || !abstractOne.getContainingClass().isInterface() && abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true)) {
        if (finals.get(signature) == null) {
          PsiSubstitutor subst = GenerateMembersUtil.correctSubstitutor(abstractOne, signature.getSubstitutor());
          CandidateInfo info = new CandidateInfo(abstractOne, subst);
          result.put(signature, info);
        }
      }
    }

    for (final MethodImplementor implementor : getImplementors()) {
      for (final PsiMethod method : implementor.getMethodsToImplement(aClass)) {
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), method.getParameterList(),
                                                                              method.getTypeParameterList(), PsiSubstitutor.EMPTY, method.isConstructor());
        CandidateInfo info = new CandidateInfo(method, PsiSubstitutor.EMPTY);
        result.put(signature, info);
      }
    }
  }

  private static boolean preferLeftForImplement(PsiMethod left, PsiMethod right) {
    if (PsiUtil.getAccessLevel(left.getModifierList()) > PsiUtil.getAccessLevel(right.getModifierList())) return true;
    if (!left.getContainingClass().isInterface()) return true;
    if (!right.getContainingClass().isInterface()) return false;
    // implement annotated method
    PsiAnnotation[] leftAnnotations = left.getModifierList().getAnnotations();
    PsiAnnotation[] rightAnnotations = right.getModifierList().getAnnotations();
    return leftAnnotations.length > rightAnnotations.length;
  }

  private static MethodImplementor[] getImplementors() {
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
  public static Collection<PsiMethod> overrideOrImplementMethod(PsiClass aClass, PsiMethod method, boolean toCopyJavaDoc) throws IncorrectOperationException {
    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);
    PsiSubstitutor substitutor = aClass.isInheritor(containingClass, true)
                                 ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY)
                                 : PsiSubstitutor.EMPTY;
    return overrideOrImplementMethod(aClass, method, substitutor, toCopyJavaDoc, CodeStyleSettingsManager.getSettings(aClass.getProject()).INSERT_OVERRIDE_ANNOTATION);
  }

  public static boolean isInsertOverride(PsiMethod superMethod, PsiClass targetClass) {
    if (!CodeStyleSettingsManager.getSettings(targetClass.getProject()).INSERT_OVERRIDE_ANNOTATION) {
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
    if (targetClass.isInterface()) return true;
    PsiClass superClass = superMethod.getContainingClass();
    return !superClass.isInterface();
  }

  @NotNull
  private static Collection<PsiMethod> overrideOrImplementMethod(PsiClass aClass,
                                                       PsiMethod method,
                                                       PsiSubstitutor substitutor,
                                                       boolean toCopyJavaDoc,
                                                       boolean insertOverrideIfPossible) throws IncorrectOperationException {
    if (!method.isValid() || !substitutor.isValid()) return Collections.emptyList();

    List<PsiMethod> results = new ArrayList<PsiMethod>();
    for (final MethodImplementor implementor : getImplementors()) {
      final PsiMethod[] prototypes = implementor.createImplementationPrototypes(aClass, method);
      if (implementor.isBodyGenerated()) {
        ContainerUtil.addAll(results, prototypes);
      }
      else {
        for (PsiMethod prototype : prototypes) {
          results.add(decorateMethod(aClass, method, toCopyJavaDoc, insertOverrideIfPossible, prototype));
        }
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
      results.add(decorateMethod(aClass, method, toCopyJavaDoc, insertOverrideIfPossible, result));
    }

    for (Iterator<PsiMethod> iterator = results.iterator(); iterator.hasNext();) {
      if (aClass.findMethodBySignature(iterator.next(), false) != null) {
        iterator.remove();
      }
    }

    return results;
  }

  private static PsiMethod decorateMethod(PsiClass aClass,
                                          PsiMethod method,
                                          boolean toCopyJavaDoc,
                                          boolean insertOverrideIfPossible,
                                          PsiMethod result) {
    PsiUtil.setModifierProperty(result, PsiModifier.ABSTRACT, aClass.isInterface());
    PsiUtil.setModifierProperty(result, PsiModifier.NATIVE, false);

    if (!toCopyJavaDoc){
      PsiDocComment comment = result.getDocComment();
      if (comment != null){
        comment.delete();
      }
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
    
    if (CodeStyleSettingsManager.getSettings(aClass.getProject()).REPEAT_SYNCHRONIZED && method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      result.getModifierList().setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }

    final PsiCodeBlock body = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createCodeBlockFromText("{}", null);
    PsiCodeBlock oldbody = result.getBody();
    if (oldbody != null){
      oldbody.replace(body);
    }
    else{
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

  public static void annotateOnOverrideImplement(PsiMethod method, PsiClass targetClass, PsiMethod overridden) {
    annotateOnOverrideImplement(method, targetClass, overridden,
                                CodeStyleSettingsManager.getSettings(method.getProject()).INSERT_OVERRIDE_ANNOTATION);
  }

  public static void annotateOnOverrideImplement(PsiMethod method, PsiClass targetClass, PsiMethod overridden, boolean insertOverride) {
    if (insertOverride && canInsertOverride(overridden, targetClass)) {
      annotate(method, Override.class.getName());
    }
    final Module module = ModuleUtil.findModuleForPsiElement(targetClass);
    final GlobalSearchScope moduleScope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : null;
    final Project project = targetClass.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    for (OverrideImplementsAnnotationsHandler each : Extensions.getExtensions(OverrideImplementsAnnotationsHandler.EP_NAME)) {
      for (String annotation : each.getAnnotations(project)) {
        if (moduleScope != null && facade.findClass(annotation, moduleScope) == null) continue;
        if (AnnotationUtil.isAnnotated(overridden, annotation, false)) {
          annotate(method, annotation, each.annotationsToRemove(project, annotation));
        }
      }
    }
  }

  public static void annotate(@NotNull PsiMethod result, String fqn, String... annosToRemove) throws IncorrectOperationException {
    Project project = result.getProject();
    AddAnnotationFix fix = new AddAnnotationFix(fqn, result, annosToRemove);
    if (fix.isAvailable(project, null, result.getContainingFile())) {
      fix.invoke(project, null, result.getContainingFile());
    }
  }

  public static boolean isOverridable(PsiMethod method) {
    return !method.isConstructor()
           && !method.hasModifierProperty(PsiModifier.STATIC)
           && !method.hasModifierProperty(PsiModifier.FINAL)
           && !method.hasModifierProperty(PsiModifier.PRIVATE);
  }

  @NotNull
  public static List<PsiGenerationInfo<PsiMethod>> overrideOrImplementMethods(PsiClass aClass,
                                                                              Collection<PsiMethodMember> candidates,
                                                                              boolean toCopyJavaDoc,
                                                                              boolean toInsertAtOverride)
    throws IncorrectOperationException {
    List<CandidateInfo> candidateInfos = ContainerUtil.map2List(candidates, new Function<PsiMethodMember, CandidateInfo>() {
      public CandidateInfo fun(final PsiMethodMember s) {
        return new CandidateInfo(s.getElement(), s.getSubstitutor());
      }
    });
    final List<PsiMethod> methods = overrideOrImplementMethodCandidates(aClass, candidateInfos, toCopyJavaDoc, toInsertAtOverride);
    return convert2GenerationInfos(methods);
  }

  @NotNull
  public static List<PsiMethod> overrideOrImplementMethodCandidates(PsiClass aClass,
                                                                    Collection<CandidateInfo> candidates,
                                                                    boolean toCopyJavaDoc,
                                                                    boolean insertOverrideWherePossible) throws IncorrectOperationException {
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    for (CandidateInfo candidateInfo : candidates) {
      result.addAll(overrideOrImplementMethod(aClass, (PsiMethod)candidateInfo.getElement(), candidateInfo.getSubstitutor(),
                                              toCopyJavaDoc, insertOverrideWherePossible));
    }
    return result;
  }

  public static List<PsiGenerationInfo<PsiMethod>> convert2GenerationInfos(final Collection<PsiMethod> methods) {
    return ContainerUtil.map2List(methods, new Function<PsiMethod, PsiGenerationInfo<PsiMethod>>() {
      public PsiGenerationInfo<PsiMethod> fun(final PsiMethod s) {
        return createGenerationInfo(s);
      }
    });
  }

  public static PsiGenerationInfo<PsiMethod> createGenerationInfo(PsiMethod s) {
    return createGenerationInfo(s, true);
  }

  public static PsiGenerationInfo<PsiMethod> createGenerationInfo(PsiMethod s, boolean mergeIfExists) {
    for (MethodImplementor implementor : getImplementors()) {
      final GenerationInfo info = implementor.createGenerationInfo(s, mergeIfExists);
      if (info instanceof PsiGenerationInfo) return (PsiGenerationInfo<PsiMethod>)info;
    }
    return new PsiGenerationInfo<PsiMethod>(s);
  }

  @NotNull
  public static String callSuper (PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
      buffer.append("return ");
    }
    buffer.append("super");
    PsiParameter[] parms = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()){
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parms.length; i++) {
      String name = parms[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }

  public static void setupMethodBody(PsiMethod result, PsiMethod originalMethod, PsiClass targetClass) throws IncorrectOperationException {
    String templName = originalMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                       JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
    setupMethodBody(result, originalMethod, targetClass, template);
  }

  public static void setupMethodBody(final PsiMethod result, final PsiMethod originalMethod, final PsiClass targetClass,
                                     final FileTemplate template) throws IncorrectOperationException {
    if (targetClass.isInterface()) {
      final PsiCodeBlock body = result.getBody();
      if (body != null) body.delete();
    }

    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
    PsiType returnType = result.getReturnType();
    if (returnType == null) {
      returnType = PsiType.VOID;
    }
    Properties properties = new Properties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(originalMethod, result));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, result);

    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), originalMethod.getProject());
    if (factory == null) factory = JavaPsiFacade.getInstance(originalMethod.getProject()).getElementFactory();
    @NonNls String methodText;
    try {
      String bodyText = template.getText(properties);
      if (bodyText != null && !bodyText.isEmpty()) bodyText += "\n";
      methodText = "void foo () {\n" + bodyText + "}";
      methodText = FileTemplateUtil.indent(methodText, result.getProject(), fileType);
    } catch (Exception e) {
      throw new IncorrectOperationException("Failed to parse file template",e);
    }
    if (methodText != null) {
      PsiMethod m;
      try {
        m = factory.createMethodFromText(methodText, originalMethod);
      }
      catch (IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(CodeInsightBundle.message("override.implement.broken.file.template.message"),
                                     CodeInsightBundle.message("override.implement.broken.file.template.title"));
          }
        });
        return;
      }
      PsiCodeBlock oldBody = result.getBody();
      if (oldBody != null) {
        oldBody.replace(m.getBody());
      }
    }
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
                                                          final boolean toImplement){
    LOG.assertTrue(aClass.isValid());
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Collection<CandidateInfo> candidates = getMethodsToOverrideImplement(aClass, toImplement);
    Collection<CandidateInfo> secondary = toImplement || aClass.isInterface() ? Collections.<CandidateInfo>emptyList() : getMethodsToOverrideImplement(aClass, true);

    final MemberChooser<PsiMethodMember> chooser = showOverrideImplementChooser(editor, aClass, toImplement, candidates, secondary);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;

    LOG.assertTrue(aClass.isValid());
    new WriteCommandAction(project, aClass.getContainingFile()) {
      protected void run(final Result result) throws Throwable {
        overrideOrImplementMethodsInRightPlace(editor, aClass, selectedElements, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());
      }
    }.execute();
  }

  @Nullable
  public static MemberChooser<PsiMethodMember> showOverrideImplementChooser(Editor editor,
                                                                            final PsiElement aClass,
                                                                            final boolean toImplement,
                                                                            Collection<CandidateInfo> candidates,
                                                                            Collection<CandidateInfo> secondary) {
    Project project = aClass.getProject();
    if (candidates.isEmpty() && secondary.isEmpty()) return null;

    final PsiMethodMember[] onlyPrimary = convertToMethodMembers(candidates);
    final PsiMethodMember[] all = ArrayUtil.mergeArrays(onlyPrimary, convertToMethodMembers(secondary));

    final String toMerge = PropertiesComponent.getInstance(project).getValue(PROP_COMBINED_OVERRIDE_IMPLEMENT);
    final Ref<Boolean> merge = Ref.create(!"false".equals(toMerge));

    final boolean isAll = merge.get().booleanValue();
    final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(isAll ? all : onlyPrimary, false, true, project,
                                                                                      PsiUtil.isLanguageLevel5OrHigher(aClass)) {

      @Override
      protected void fillToolbarActions(DefaultActionGroup group) {
        super.fillToolbarActions(group);
        if (toImplement) return;

        final ToggleAction mergeAction = new ToggleAction("Show methods to implement", "Show methods to implement",
                                                          AllIcons.General.Show_to_implement) {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return merge.get().booleanValue();
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            merge.set(state);
            resetElements(state ? all : onlyPrimary);
            setTitle(getChooserTitle(false, merge));
          }
        };
        mergeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_MASK)), myTree);

        Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("OverrideMethods");
        mergeAction.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);

        group.add(mergeAction);
      }
    };
    chooser.setTitle(getChooserTitle(toImplement, merge));
    registerHandlerForComplementaryAction(project, editor, aClass, toImplement, chooser);

    chooser.setCopyJavadocVisible(true);

    if (toImplement) {
      chooser.selectElements(isAll ? all : onlyPrimary);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      chooser.selectElements(all);
      chooser.close(DialogWrapper.OK_EXIT_CODE);
      return chooser;
    }

    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

    PropertiesComponent.getInstance(project).setValue(PROP_COMBINED_OVERRIDE_IMPLEMENT, merge.get().toString());
    return chooser;
  }

  private static String getChooserTitle(boolean toImplement, Ref<Boolean> merge) {
    return toImplement
                     ? CodeInsightBundle.message("methods.to.implement.chooser.title")
                     : merge.get().booleanValue()
                       ? CodeInsightBundle.message("methods.to.override.implement.chooser.title")
                       : CodeInsightBundle.message("methods.to.override.chooser.title");
  }

  private static PsiMethodMember[] convertToMethodMembers(Collection<CandidateInfo> candidates) {
    return ContainerUtil.map2Array(candidates, PsiMethodMember.class, new Function<CandidateInfo, PsiMethodMember>() {
        public PsiMethodMember fun(final CandidateInfo s) {
          return new PsiMethodMember(s);
        }
      });
  }

  private static void registerHandlerForComplementaryAction(final Project project, final Editor editor, final PsiElement aClass,
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
            public void actionPerformed(final ActionEvent e) {
              chooser.close(DialogWrapper.CANCEL_EXIT_CODE);

              // invoke later in order to close previous modal dialog
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  final CodeInsightActionHandler handler = toImplement ? new OverrideMethodsHandler(): new ImplementMethodsHandler();
                  handler.invoke(project, editor, aClass.getContainingFile());
                }
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
      if (aClass.getLBrace() == null) {
        PsiClass psiClass = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createClass("X");
        aClass.addRangeAfter(psiClass.getLBrace(), psiClass.getRBrace(), aClass.getLastChild());
      }

      int lbraceOffset = aClass.getLBrace().getTextOffset();
      List<PsiGenerationInfo<PsiMethod>> resultMembers;
      if (offset <= lbraceOffset || aClass.isEnum()) {
        resultMembers = new ArrayList<PsiGenerationInfo<PsiMethod>>();
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
        resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
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
        if (prevMethod != null){
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
        if (nextMethod != null){
          return nextMethod;
        }
      }
      nextBaseMethod = PsiTreeUtil.getNextSiblingOfType(nextBaseMethod, PsiMethod.class);
    }

    return null;
  }

  public static void overrideOrImplement(PsiClass psiClass, @NotNull PsiMethod baseMethod) throws IncorrectOperationException {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(baseMethod.getProject());

    List<PsiGenerationInfo<PsiMethod>> prototypes = convert2GenerationInfos(overrideOrImplementMethod(psiClass, baseMethod, false));
    if (prototypes.isEmpty()) return;

    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseMethod.getContainingClass(), psiClass, PsiSubstitutor.EMPTY);
    PsiElement anchor = getDefaultAnchorToOverrideOrImplement(psiClass, baseMethod, substitutor);
    List<PsiGenerationInfo<PsiMethod>> results = GenerateMembersUtil.insertMembersBeforeAnchor(psiClass, anchor, prototypes);

    PsiFile psiFile = psiClass.getContainingFile();
    Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), false);
    if (editor == null) return;

    results.get(0).positionCaret(editor, true);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  @Nullable
  public static PsiClass getContextClass(Project project, Editor editor, PsiFile file, boolean allowInterface) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass)element;
    if (aClass instanceof JspClass) return null;
    return aClass == null || !allowInterface && aClass.isInterface() ? null : aClass;
  }

  public static void overrideOrImplementMethodsInRightPlace(Editor editor1, PsiClass aClass, Collection<PsiMethodMember> members, boolean copyJavadoc) {
    boolean insert = CodeStyleSettingsManager.getSettings(aClass.getProject()).INSERT_OVERRIDE_ANNOTATION;
    overrideOrImplementMethodsInRightPlace(editor1, aClass, members, copyJavadoc, insert);
  }

  public static List<PsiMethod> overrideOrImplementMethodCandidates(PsiClass aClass, Collection<CandidateInfo> candidatesToImplement,
                                                                    boolean copyJavadoc) throws IncorrectOperationException {
    boolean insert = CodeStyleSettingsManager.getSettings(aClass.getProject()).INSERT_OVERRIDE_ANNOTATION;
    return overrideOrImplementMethodCandidates(aClass, candidatesToImplement, copyJavadoc, insert);
  }

  public static class MethodSignatureComparator implements Comparator<MethodSignature> {
    // signatures should appear in the order of declaration
    public int compare(MethodSignature o1, MethodSignature o2) {
      if (o1 instanceof MethodSignatureBackedByPsiMethod && o2 instanceof MethodSignatureBackedByPsiMethod) {
        PsiMethod m1 = ((MethodSignatureBackedByPsiMethod)o1).getMethod();
        PsiMethod m2 = ((MethodSignatureBackedByPsiMethod)o2).getMethod();
        PsiClass c1 = m1.getContainingClass();
        PsiClass c2 = m2.getContainingClass();
        if (c1 != null && c2 != null) {
          if (c1 == c2) {
            final List<PsiMethod> methods = Arrays.asList(c1.getMethods());
            return methods.indexOf(m1) - methods.indexOf(m2);
          }

          if (c1.isInheritor(c2, true)) return -1;
          if (c2.isInheritor(c1, true)) return 1;

          return StringUtil.notNullize(c1.getName()).compareTo(StringUtil.notNullize(c2.getName()));
        }
        return m1.getTextOffset() - m2.getTextOffset();
      }
      return 0;
    }
  }
}

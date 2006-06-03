package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class OverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.OverrideImplementUtil");

  private OverrideImplementUtil() {
  }

  @NotNull
  public static CandidateInfo[] getMethodsToOverrideImplement(PsiClass aClass, boolean toImplement) {
    Collection<CandidateInfo> infos = getMapToOverrideImplement(aClass, toImplement).values();
    CandidateInfo[] result = new CandidateInfo[infos.size()];
    int i = 0;
    for (final CandidateInfo info : infos) {
      result[i++] = info;
    }
    return result;
  }

  @NotNull
  public static MethodSignature[] getMethodSignaturesToImplement(PsiClass aClass) {
    Set<MethodSignature> signatures = getMapToOverrideImplement(aClass, true).keySet();
    return signatures.toArray(new MethodSignature[signatures.size()]);
  }

  @NotNull
  public static MethodSignature[] getMethodSignaturesToOverride(PsiClass aClass) {
    Set<MethodSignature> signatures = getMapToOverrideImplement(aClass, false).keySet();
    return signatures.toArray(new MethodSignature[signatures.size()]);
  }

  @NotNull
  private static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(PsiClass aClass,
                                                                               boolean toImplement) {
    final PsiSubstitutor contextSubstitutor = getContextSubstitutor(aClass);
    Map<MethodSignature, PsiMethod> abstracts = new LinkedHashMap<MethodSignature,PsiMethod>();
    Map<MethodSignature, PsiMethod> finals = new HashMap<MethodSignature,PsiMethod>();
    Map<MethodSignature, PsiMethod> concretes = new LinkedHashMap<MethodSignature,PsiMethod>();
    Map<PsiClass, PsiSubstitutor> substitutors = new HashMap<PsiClass,PsiSubstitutor>();

    PsiMethod[] allMethods = aClass.getAllMethods();
    PsiResolveHelper resolveHelper = aClass.getManager().getResolveHelper();
    for (PsiMethod method : allMethods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) || !resolveHelper.isAccessible(method, aClass, aClass)) continue;
      PsiClass hisClass = method.getContainingClass();
      //Filter non-immediate super constructors
      if (method.isConstructor() && (!aClass.isInheritor(hisClass, false) || aClass instanceof PsiAnonymousClass || aClass.isEnum())) {
        continue;
      }

      PsiSubstitutor substitutor;
      if ((substitutor = substitutors.get(hisClass)) == null) {
        substitutor = aClass.isInheritor(hisClass, true) ?
                      TypeConversionUtil.getSuperClassSubstitutor(hisClass, aClass, PsiSubstitutor.EMPTY) : PsiSubstitutor.EMPTY;
        substitutor = substitutor.putAll(contextSubstitutor);
        substitutors.put(hisClass, substitutor);
      }

      String name = method.isConstructor() ? aClass.getName() : method.getName();
      substitutor = GenerateMembersUtil.correctSubstitutor(method, substitutor);

      MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, method.getParameterList(), method.getTypeParameterList(),
                                                                            substitutor);
      if (MethodSignatureUtil.findMethodBySignature(aClass, signature, false) != null) continue;

      if (method.hasModifierProperty(PsiModifier.FINAL)) {
        finals.put(signature, method);
        continue;
      }

      Map<MethodSignature, PsiMethod> map = hisClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)
                                            ? abstracts
                                            : concretes;
      PsiMethod other = map.get(signature);
      if (other == null || PsiUtil.getAccessLevel(method.getModifierList()) > PsiUtil.getAccessLevel(other.getModifierList())) {
        map.put(signature, method);
      }
    }

    Map<MethodSignature, CandidateInfo> result = new LinkedHashMap<MethodSignature,CandidateInfo>();
    if (toImplement || aClass.isInterface()) {
      for (Map.Entry<MethodSignature, PsiMethod> entry : abstracts.entrySet()) {
        MethodSignature signature = entry.getKey();
        PsiMethod abstractOne = entry.getValue();
        PsiMethod concrete = concretes.get(signature);
        if (concrete == null ||
            PsiUtil.getAccessLevel(concrete.getModifierList()) < PsiUtil
              .getAccessLevel(abstractOne.getModifierList()) ||
                                                             (!abstractOne.getContainingClass().isInterface() &&
                                                              abstractOne.getContainingClass()
                                                                .isInheritor(concrete.getContainingClass(), true))) {
          if (finals.get(signature) == null) {
            PsiSubstitutor subst = GenerateMembersUtil.correctSubstitutor(abstractOne,
                                                                          substitutors.get(abstractOne.getContainingClass()));
            CandidateInfo info = new CandidateInfo(abstractOne, subst);
            result.put(signature, info);
          }
        }
      }

      for (final MethodImplementor implementor : getImplementors()) {
        for (final PsiMethod method : implementor.getMethodsToImplement(aClass)) {
          MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), method.getParameterList(),
                                                                                method.getTypeParameterList(), PsiSubstitutor.EMPTY);
          CandidateInfo info = new CandidateInfo(method, PsiSubstitutor.EMPTY);
          result.put(signature, info);
        }
      }
    } else {
      for (Map.Entry<MethodSignature, PsiMethod> entry : concretes.entrySet()) {
        MethodSignature signature = entry.getKey();
        PsiMethod concrete = entry.getValue();
        if (finals.get(signature) == null) {
          PsiMethod abstractOne = abstracts.get(signature);
          if (abstractOne == null || !abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true) ||
              concrete.getContainingClass().getQualifiedName().equals("java.lang.Object")) {
            PsiSubstitutor subst = GenerateMembersUtil.correctSubstitutor(concrete, substitutors.get(concrete.getContainingClass()));
            CandidateInfo info = new CandidateInfo(concrete, subst);
            result.put(signature, info);
          }
        }
      }
    }

    return result;
  }

  private static MethodImplementor[] getImplementors() {
    return ApplicationManager.getApplication().getComponents(MethodImplementor.class);
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
  public static PsiMethod[] overrideOrImplementMethod(PsiClass aClass, PsiMethod method, boolean toCopyJavaDoc) throws IncorrectOperationException {
    final PsiClass containingClass = method.getContainingClass();
    PsiSubstitutor substitutor = aClass.isInheritor(containingClass, true) ?
                                 TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY) : PsiSubstitutor.EMPTY;
    return overrideOrImplementMethod(aClass, method, substitutor, toCopyJavaDoc, false);
  }

  @NotNull
  private static PsiMethod[] overrideOrImplementMethod(PsiClass aClass,
                                                       PsiMethod method,
                                                       PsiSubstitutor substitutor,
                                                       boolean toCopyJavaDoc,
                                                       boolean insertAtOverride) throws IncorrectOperationException {
    if (!method.isValid() || !substitutor.isValid()) return PsiMethod.EMPTY_ARRAY;

    List<PsiMethod> results = new ArrayList<PsiMethod>();
    for (final MethodImplementor implementor : getImplementors()) {
      results.addAll(Arrays.asList(implementor.createImplementationPrototypes(aClass, method)));
    }
    if (results.isEmpty()) {
      PsiMethod method1 = substitutor != PsiSubstitutor.EMPTY ?
                          GenerateMembersUtil.substituteGenericMethod(method, substitutor) : method;

      PsiElementFactory factory = method.getManager().getElementFactory();
      PsiMethod result = (PsiMethod)factory.createClass("Dummy").add(method1);
      results.add(result);
    }

    for (Iterator<PsiMethod> iterator = results.iterator(); iterator.hasNext();) {
      PsiMethod result = iterator.next();
      result.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, aClass.isInterface());
      result.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

      if (!toCopyJavaDoc){
        PsiDocComment comment = result.getDocComment();
        if (comment != null){
          comment.delete();
        }
      }

      if (insertAtOverride && !method.isConstructor()) {
        PsiModifierList modifierList = result.getModifierList();
        if (modifierList.findAnnotation("java.lang.Override") == null) {
          PsiAnnotation annotation = method.getManager().getElementFactory().createAnnotationFromText("@java.lang.Override", null);
          modifierList.addAfter(annotation, null);
        }
      }
      if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(aClass)) <= 0 &&
          method.getModifierList().findAnnotation(AnnotationUtil.NOT_NULL) != null &&
          result.getModifierList().findAnnotation(AnnotationUtil.NOT_NULL) == null) {
        PsiAnnotation annotation = method.getManager().getElementFactory().createAnnotationFromText("@" + AnnotationUtil.NOT_NULL, null);
        result.getModifierList().addAfter(annotation, null);
      }

      final PsiCodeBlock body = method.getManager().getElementFactory().createCodeBlockFromText("{}", null);
      if (result.getBody() != null){
        result.getBody().replace(body);
      }
      else{
        result.add(body);
      }

      setupMethodBody(result, method, aClass);

      // probably, it's better to reformat the whole method - it can go from other style sources
      CodeStyleManager codeStyleManager = method.getManager().getCodeStyleManager();
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(codeStyleManager.getProject());
      boolean keepBreaks = settings.KEEP_LINE_BREAKS;
      settings.KEEP_LINE_BREAKS = false;
      result = (PsiMethod)codeStyleManager.shortenClassReferences(result);
      result = (PsiMethod)codeStyleManager.reformat(result);
      settings.KEEP_LINE_BREAKS = keepBreaks;

      if (aClass.findMethodBySignature(result, false) != null) {
        iterator.remove();
      }
    }

    return results.toArray(new PsiMethod[results.size()]);
  }

  public static boolean isOverridable(PsiMethod method) {
    return !method.isConstructor()
           && !method.hasModifierProperty(PsiModifier.STATIC)
           && !method.hasModifierProperty(PsiModifier.FINAL)
           && !method.hasModifierProperty(PsiModifier.PRIVATE);
  }

  @NotNull
  public static PsiMethod[] overrideOrImplementMethods(PsiClass aClass,
                                                       CandidateInfo[] candidates,
                                                       boolean toCopyJavaDoc,
                                                       boolean toInsertAtOverride) throws IncorrectOperationException {
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    for (CandidateInfo candidateInfo : candidates) {
      result.addAll(Arrays.asList(overrideOrImplementMethod(aClass, (PsiMethod)candidateInfo.getElement(), candidateInfo.getSubstitutor(),
                                                            toCopyJavaDoc, toInsertAtOverride)));
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  @NotNull
  private static String callSuper (PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuffer buffer = new StringBuffer();
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
                       FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;
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
    FileTemplateUtil.setClassAndMethodNameProperties(properties, targetClass, result);

    PsiElementFactory factory = originalMethod.getManager().getElementFactory();
    @NonNls String methodText;
    try {
      String bodyText = template.getText(properties);
      if (!"".equals(bodyText)) bodyText += "\n";
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
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.overrideimplement");
    chooseAndOverrideOrImplementMethods(project, editor, aClass, false);
  }

  public static void chooseAndImplementMethods(Project project, Editor editor, PsiClass aClass){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.overrideimplement");
    chooseAndOverrideOrImplementMethods(project, editor, aClass, true);
  }

  private static void chooseAndOverrideOrImplementMethods(final Project project,
                                                          final Editor editor,
                                                          final PsiClass aClass,
                                                          boolean toImplement){
    LOG.assertTrue(aClass.isValid());
    ApplicationManager.getApplication().assertReadAccessAllowed();

    CandidateInfo[] candidates = getMethodsToOverrideImplement(aClass, toImplement);

    if (candidates.length == 0) return;

    boolean isJdk15Enabled = LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(aClass)) <= 0;
    final MemberChooser chooser = new MemberChooser(candidates, false, true, project, !toImplement && isJdk15Enabled);
    chooser.setTitle(toImplement
                     ? CodeInsightBundle.message("methods.to.implement.chooser.title")
                     : CodeInsightBundle.message("methods.to.override.chooser.title"));
    chooser.setCopyJavadocVisible(true);
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    Object[] selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.length == 0) return;

    final CandidateInfo[] selectedCandidates = new CandidateInfo[selectedElements.length];
    for (int i = 0; i < selectedCandidates.length; i++) {
      selectedCandidates[i] = (CandidateInfo) selectedElements[i];
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        overrideOrImplementMethodsInRightPlace(editor, aClass, selectedCandidates, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());
      }
    });
  }

  public static void overrideOrImplementMethodsInRightPlace(Editor editor,
                                                            PsiClass aClass,
                                                            CandidateInfo[] candidates,
                                                            boolean copyJavadoc,
                                                            boolean insertAtOverride) {
    try{
      Object[] resultMembers;

      int offset = editor.getCaretModel().getOffset();
      int lbraceOffset = aClass.getLBrace().getTextOffset();
      if (offset <= lbraceOffset || aClass.isEnum()){
        ArrayList<PsiElement> list = new ArrayList<PsiElement>();
        for (CandidateInfo candidate : candidates) {
          PsiMethod[] prototypes = overrideOrImplementMethod(aClass, (PsiMethod)candidate.getElement(), candidate.getSubstitutor(),
                                                             copyJavadoc, insertAtOverride);
          for (PsiMethod prototype : prototypes) {
            PsiElement anchor = getDefaultAnchorToOverrideOrImplement(aClass, (PsiMethod)candidate.getElement(),
                                                                      candidate.getSubstitutor());
            PsiElement result;
            if (anchor != null) {
              result = aClass.addBefore(prototype, anchor);
            }
            else {
              result = aClass.add(prototype);
            }
            list.add(result);
          }
        }
        resultMembers = list.toArray(new Object[list.size()]);
      }
      else{
        PsiMethod[] prototypes = overrideOrImplementMethods(aClass, candidates, copyJavadoc, insertAtOverride);
        resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
      }

      GenerateMembersUtil.positionCaret(editor, (PsiElement)resultMembers[0], true);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  public static PsiElement getDefaultAnchorToOverrideOrImplement(PsiClass aClass, PsiMethod baseMethod, PsiSubstitutor substitutor){
    PsiMethod prevBaseMethod = PsiTreeUtil.getPrevSiblingOfType(baseMethod, PsiMethod.class);
    while(prevBaseMethod != null) {
      String name = prevBaseMethod.isConstructor() ? aClass.getName() : prevBaseMethod.getName();
      //Happens when aClass instanceof PsiAnonymousClass
      if (name != null) {
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, prevBaseMethod.getParameterList(), prevBaseMethod.getTypeParameterList(), substitutor);
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
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, nextBaseMethod.getParameterList(), nextBaseMethod.getTypeParameterList(), substitutor);
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

    PsiMethod[] prototypes = overrideOrImplementMethod(psiClass, baseMethod, false);
    if (prototypes.length == 0) return;
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseMethod.getContainingClass(), psiClass, PsiSubstitutor.EMPTY);
    PsiElement anchor = getDefaultAnchorToOverrideOrImplement(psiClass, baseMethod, substitutor);
    Object[] results = GenerateMembersUtil.insertMembersBeforeAnchor(psiClass, anchor, prototypes);

    PsiFile psiFile = psiClass.getContainingFile();
    Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), false);
    GenerateMembersUtil.positionCaret(editor, (PsiElement)results[0], true);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  public static PsiClass getContextClass(Project project, Editor editor, PsiFile file, boolean allowInterface) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass)element;
    return aClass == null || (!allowInterface && aClass.isInterface()) ? null : aClass;
  }

  private static PsiSubstitutor getContextSubstitutor(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)aClass).getBaseClassType().resolveGenerics().getSubstitutor();
    }

    return PsiSubstitutor.EMPTY;
  }
}

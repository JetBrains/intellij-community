// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.intention.impl.AddJavadocIntention;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.*;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.intellij.util.ObjectUtils.notNull;

public final class MissingJavadocInspection extends LocalInspectionTool {
  private static final ExtensionPointName<Condition<PsiMember>> EP_NAME = new ExtensionPointName<>("com.intellij.javaDocNotNecessary");

  public boolean IGNORE_DEPRECATED_ELEMENTS = false;
  public boolean IGNORE_ACCESSORS = false;
  public Options PACKAGE_SETTINGS = new Options();
  public Options MODULE_SETTINGS = new Options();
  public Options TOP_LEVEL_CLASS_SETTINGS = new Options("@param");
  public Options INNER_CLASS_SETTINGS = new Options("@param");
  public Options METHOD_SETTINGS = new Options("@return@param@throws or @exception");
  public Options FIELD_SETTINGS = new Options();

  private static final String PACKAGE_LOCAL = "package";
  static final String PUBLIC = PsiModifier.PUBLIC;
  private static final String PROTECTED = PsiModifier.PROTECTED;
  private static final String PRIVATE = PsiModifier.PRIVATE;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_DEPRECATED_ELEMENTS", JavaBundle.message("inspection.javadoc.option.ignore.deprecated")),
      checkbox("IGNORE_ACCESSORS", JavaBundle.message("inspection.javadoc.option.ignore.simple")),
      checkboxPanel(
        PACKAGE_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title.package"),
                                      List.of(), List.of("@author", "@version", "@since"))
          .prefix("PACKAGE_SETTINGS"),
        MODULE_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title.module"),
                                     List.of(), List.of("@author", "@version", "@since"))
          .prefix("MODULE_SETTINGS"),
        TOP_LEVEL_CLASS_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title"),
                                              List.of(PUBLIC, PACKAGE_LOCAL),
                                              List.of("@author", "@version", "@since", "@param"))
          .prefix("TOP_LEVEL_CLASS_SETTINGS"),
        METHOD_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title.method"),
                                     List.of(PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE),
                                     List.of("@return", "@param", NlsMessages.formatOrList(List.of("@throws", "@exception"))))
          .prefix("METHOD_SETTINGS"),
        FIELD_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title.field"),
                                    List.of(PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE), List.of())
          .prefix("FIELD_SETTINGS"),
        INNER_CLASS_SETTINGS.getComponent(JavaBundle.message("inspection.javadoc.option.tab.title.inner.class"),
                                          List.of(PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE), List.of("@param"))
          .prefix("INNER_CLASS_SETTINGS")
      )
    );
  }

  public static class Options implements OptionContainer {
    public String MINIMAL_VISIBILITY = "public";
    public String REQUIRED_TAGS = "";
    public boolean ENABLED = true;

    public Options() {}

    public Options(String tags){
      REQUIRED_TAGS = tags;
    }

    public boolean isTagRequired(String tag) {
      return REQUIRED_TAGS.contains(tag);
    }

    public void setTagRequired(String tag, boolean value) {
      if (value) {
        if (!isTagRequired(tag)) REQUIRED_TAGS += tag;
      } else {
        REQUIRED_TAGS = REQUIRED_TAGS.replaceAll(tag, "");
      }
    }

    @Override
    public @NotNull OptionController getOptionController() {
      return OptionController.fieldsOf(this)
        .onPrefix("REQUIRED_TAGS", OptionController.of(tag -> isTagRequired(tag), (tag, value) -> setTagRequired(tag, (boolean)value)));
    }
    
    @NotNull OptCheckbox getComponent(@NotNull @NlsContexts.Label String label,
                                      @NotNull List<@NlsSafe String> visibility,
                                      @NotNull List<@NlsSafe String> tags) {
      List<OptRegularComponent> controls = new ArrayList<>();
      if (!visibility.isEmpty()) {
        controls.add(dropdown("MINIMAL_VISIBILITY", JavaBundle.message("inspection.missingJavadoc.label.minimalVisibility"),
                              visibility, Function.identity(), Function.identity()));
      }
      if (!tags.isEmpty()) {
        //noinspection LanguageMismatch
        controls.add(group(JavaBundle.message("inspection.missingJavadoc.label.requiredTags"),
                           ContainerUtil.map2Array(tags, OptRegularComponent.class, tag -> checkbox(tag, tag)))
                       .prefix("REQUIRED_TAGS"));
      }
      return new OptPane(controls).asCheckbox("ENABLED", label);
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@NotNull PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkFile(file, holder, isOnTheFly);
        }
      }

      @Override
      public void visitModule(@NotNull PsiJavaModule module) {
        checkModule(module, holder, isOnTheFly);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        checkClass(aClass, holder, isOnTheFly);
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        checkField(field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkMethod(method, holder, isOnTheFly);
      }
    };
  }

  private void checkFile(PsiJavaFile file, ProblemsHolder holder, boolean isOnTheFly) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    if (pkg == null) return;

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(pkg, docComment)) {
      return;
    }
    if (!isJavadocRequired(PACKAGE_SETTINGS, pkg)) return;

    if (docComment != null) {
      checkRequiredTags(docComment.getTags(), PACKAGE_SETTINGS, docComment.getFirstChild(), holder);
    }
    else {
      PsiElement toHighlight = notNull(file.getPackageStatement(), file);
      reportMissingJavadoc(toHighlight, holder, isOnTheFly);
    }
  }

  private void checkModule(PsiJavaModule module, ProblemsHolder holder, boolean isOnTheFly) {
    PsiDocComment docComment = module.getDocComment();
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(module, docComment)) {
      return;
    }
    if (!isJavadocRequired(MODULE_SETTINGS, module)) return;

    if (docComment != null) {
      checkRequiredTags(docComment.getTags(), MODULE_SETTINGS, docComment.getFirstChild(), holder);
    }
    else {
      reportMissingJavadoc(module.getNameIdentifier(), holder, isOnTheFly);
    }
  }

  private void checkClass(PsiClass aClass, ProblemsHolder holder, boolean isOnTheFly) {
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiSyntheticClass || aClass instanceof PsiTypeParameter) {
      return;
    }
    if (IGNORE_DEPRECATED_ELEMENTS && aClass.isDeprecated()) {
      return;
    }
    Options options = ClassUtil.isTopLevelClass(aClass) ? TOP_LEVEL_CLASS_SETTINGS : INNER_CLASS_SETTINGS;
    if (!isJavadocRequired(options, aClass)) return;

    PsiDocComment docComment = aClass.getDocComment();
    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();

      PsiElement toHighlight = docComment.getFirstChild();
      checkRequiredTags(tags, options, toHighlight, holder);

      if (options.isTagRequired("param")) {
        checkMissingTypeParamTags(aClass, tags, toHighlight, holder);
        checkMissingParamTags(aClass, tags, toHighlight, holder);
      }
    }
    else {
      PsiElement toHighlight = notNull(aClass.getNameIdentifier(), aClass);
      reportMissingJavadoc(toHighlight, holder, isOnTheFly);
    }
  }

  private void checkField(PsiField field, ProblemsHolder holder, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(field)) {
      return;
    }
    if (!isJavadocRequired(FIELD_SETTINGS, field)) return;

    PsiDocComment docComment = field.getDocComment();
    if (docComment != null) {
      checkRequiredTags(docComment.getTags(), FIELD_SETTINGS, docComment.getFirstChild(), holder);
    }
    else {
      reportMissingJavadoc(field.getNameIdentifier(), holder, isOnTheFly);
    }
  }

  private void checkMethod(PsiMethod method, ProblemsHolder holder, boolean isOnTheFly) {
    if (method instanceof SyntheticElement) {
      return;
    }
    if (IGNORE_DEPRECATED_ELEMENTS && isDeprecated(method)) {
      return;
    }
    if (IGNORE_ACCESSORS && PropertyUtilBase.isSimplePropertyAccessor(method)) {
      return;
    }
    if (!isJavadocRequired(METHOD_SETTINGS, method) || method.findSuperMethods().length > 0) return;

    PsiDocComment docComment = method.getDocComment();
    if (docComment != null) {
      if (!isInherited(docComment, method)) {
        PsiDocTag[] tags = docComment.getTags();

        PsiElement toHighlight = docComment.getFirstChild();
        if (METHOD_SETTINGS.isTagRequired("return")) {
          checkMissingReturnTag(method, tags, toHighlight, holder);
        }
        if (METHOD_SETTINGS.isTagRequired("param")) {
          checkMissingParamTags(method, tags, toHighlight, holder);
          checkMissingTypeParamTags(method, tags, toHighlight, holder);
        }
        if (METHOD_SETTINGS.isTagRequired("throws")) {
          checkMissingThrowsTags(method, tags, toHighlight, holder);
        }
      }
    }
    else {
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null && isJavadocRequired(method)) {
          reportMissingJavadoc(nameIdentifier, holder, isOnTheFly);
      }
    }
  }

  static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated() || element.getContainingClass() != null && element.getContainingClass().isDeprecated();
  }

  static boolean isInherited(PsiDocComment docComment, PsiMethod psiMethod) {
    for (PsiElement descriptionElement : docComment.getDescriptionElements()) {
      if (descriptionElement instanceof PsiInlineDocTag tag && "inheritDoc".equals(tag.getName())) {
        return true;
      }
    }

    if (docComment.findTagByName("inheritDoc") != null) {
      JavadocTagInfo tagInfo = JavadocManager.getInstance(psiMethod.getProject()).getTagInfo("inheritDoc");
      if (tagInfo != null && tagInfo.isValidInContext(psiMethod)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isJavadocRequired(PsiMethod method){
    return !ContainerUtil.exists(EP_NAME.getExtensionList(), condition -> condition.value(method));
  }

  private static boolean isJavadocRequired(@NotNull Options options, @NotNull PsiModifierListOwner element) {
    if (!options.ENABLED) return false;
    if (element instanceof PsiPackage) {
      return true;
    }

    if (element instanceof PsiJavaModule) {
      return true;
    }

    int actualAccess = getAccessNumber(RefJavaUtil.getInstance().getAccessModifier(element));

    if (element instanceof PsiClass) {
      return actualAccess <= getAccessNumber(options.MINIMAL_VISIBILITY);
    }

    if (element instanceof PsiMember) {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      while (element != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(RefJavaUtil.getInstance().getAccessModifier(element)));
        element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(options.MINIMAL_VISIBILITY);
    }

    return false;
  }

  private static int getAccessNumber(String accessModifier) {
    if (accessModifier.startsWith(PUBLIC)) return 1;
    if (accessModifier.startsWith(PROTECTED)) return 2;
    if (accessModifier.startsWith(PACKAGE_LOCAL)) return 3;
    if (accessModifier.startsWith(PRIVATE)) return 4;

    return 5;
  }

  static boolean isDeprecated(PsiModifierListOwner element, PsiDocComment docComment) {
    return PsiImplUtil.isDeprecatedByAnnotation(element) || docComment != null && docComment.findTagByName("deprecated") != null;
  }

  private static void checkMissingReturnTag(@NotNull PsiMethod psiMethod,
                                            PsiDocTag @NotNull [] tags,
                                            @NotNull PsiElement toHighlight,
                                            @NotNull ProblemsHolder holder) {
    if (!psiMethod.isConstructor() && !PsiTypes.voidType().equals(psiMethod.getReturnType())) {
      boolean hasReturnTag = ContainerUtil.exists(tags, tag -> "return".equals(tag.getName()));
      if (!hasReturnTag) {
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", "<code>@return</code>");
        problem(holder, toHighlight, message, new JavaDocFixes.AddMissingTagFix("return", ""));
      }
    }
  }

  private static void checkMissingThrowsTags(@NotNull PsiMethod psiMethod,
                                             PsiDocTag @NotNull [] tags,
                                             @NotNull PsiElement toHighlight,
                                             @NotNull ProblemsHolder holder) {
    PsiClassType[] thrownTypes = psiMethod.getThrowsList().getReferencedTypes();
    if (thrownTypes.length <= 0) return;

    Map<PsiClassType, PsiClass> declaredExceptions = new LinkedHashMap<>();

    for (PsiClassType classType : thrownTypes) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        declaredExceptions.put(classType, psiClass);
      }
    }

    for (PsiDocTag tag : tags) {
      if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          PsiElement firstChild = value.getFirstChild();
          if (firstChild != null) {
            PsiElement psiElement = firstChild.getFirstChild();
            if ((psiElement instanceof PsiJavaCodeReferenceElement ref) && ref.resolve() instanceof PsiClass aClass) {
              for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext(); ) {
                PsiClass psiClass = declaredExceptions.get(it.next());
                if (InheritanceUtil.isInheritorOrSelf(aClass, psiClass, true)) {
                  it.remove();
                }
              }
            }
          }
        }
      }
    }

    for (PsiClassType declaredException : declaredExceptions.keySet()) {
      String tagText = "<code>@throws</code> " + declaredException.getCanonicalText();
      String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", tagText);
      String firstDeclaredException = declaredException.getCanonicalText();
      problem(holder, toHighlight, message, new JavaDocFixes.AddMissingTagFix("throws", firstDeclaredException));
    }
  }

  private static void checkMissingParamTags(@NotNull PsiClass aClass,
                                            PsiDocTag @NotNull [] tags,
                                            @NotNull PsiElement toHighlight,
                                            @NotNull ProblemsHolder holder) {
    if (!aClass.isRecord()) return;
    List<PsiNamedElement> absentParameters = null;

    for (PsiRecordComponent param : aClass.getRecordComponents()) {
      if (!hasTagForParameter(tags, param)) {
        (absentParameters = list(absentParameters)).add(param);
      }
    }

    reportMissingParamTags(absentParameters, toHighlight, holder);
  }

  private static void checkMissingParamTags(@NotNull PsiMethod psiMethod,
                                            PsiDocTag @NotNull [] tags,
                                            @NotNull PsiElement toHighlight,
                                            @NotNull ProblemsHolder holder) {
    List<PsiNamedElement> absentParameters = null;

    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      if (!hasTagForParameter(tags, param)) {
        (absentParameters = list(absentParameters)).add(param);
      }
    }

    reportMissingParamTags(absentParameters, toHighlight, holder);
  }

  private static void reportMissingParamTags(List<PsiNamedElement> absentParameters,
                                             @NotNull PsiElement toHighlight,
                                             @NotNull ProblemsHolder holder) {
    if (absentParameters != null) {
      for (PsiNamedElement parameter : absentParameters) {
        String name = parameter.getName();
        if (name != null) {
          String message = JavaBundle.message("inspection.javadoc.method.problem.missing.param.tag", "<code>" + name + "</code>");
          problem(holder, toHighlight, message, new JavaDocFixes.AddMissingParamTagFix(name));
        }
      }
    }
  }

  private static void reportMissingJavadoc(@NotNull PsiElement toHighlight, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    String message = JavaBundle.message("inspection.javadoc.problem.descriptor");
    LocalQuickFix fix = isOnTheFly ? LocalQuickFix.from(new AddJavadocIntention()) : null;
    problem(holder, toHighlight, message, fix);
  }

  private static void problem(@NotNull ProblemsHolder holder,
                              @NotNull PsiElement toHighlight,
                              @NotNull @Nls String message,
                              @Nullable LocalQuickFix fix) {
    final LocalQuickFix[] fixes = fix != null ? new LocalQuickFix[] { fix } : null;
    holder.registerProblem(toHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  private static final String[] TAGS_TO_CHECK = {"author", "version", "since"};

  private static void checkRequiredTags(PsiDocTag @NotNull [] tags,
                                        @NotNull Options options,
                                        @NotNull PsiElement toHighlight,
                                        @NotNull ProblemsHolder holder) {
    boolean[] isTagRequired = new boolean[TAGS_TO_CHECK.length];
    boolean[] isTagPresent = new boolean[TAGS_TO_CHECK.length];
    boolean someTagsAreRequired = false;

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      someTagsAreRequired |= (isTagRequired[i] = options.isTagRequired(TAGS_TO_CHECK[i]));
    }

    if (!someTagsAreRequired) return;

    for (PsiDocTag tag : tags) {
      int p = ArrayUtil.find(TAGS_TO_CHECK, tag.getName());
      if (p >= 0) isTagPresent[p] = true;
    }

    for (int i = 0; i < TAGS_TO_CHECK.length; i++) {
      if (isTagRequired[i] && !isTagPresent[i]) {
        String tagName = TAGS_TO_CHECK[i];
        String message = JavaBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tagName + "</code>");
        problem(holder, toHighlight, message, new JavaDocFixes.AddMissingTagFix(tagName, ""));
      }
    }
  }

  private static void checkMissingTypeParamTags(@NotNull PsiTypeParameterListOwner owner,
                                                PsiDocTag @NotNull [] tags,
                                                @NotNull PsiElement toHighlight,
                                                @NotNull ProblemsHolder holder) {
    if (!owner.hasTypeParameters()) return;
    List<PsiTypeParameter> absentParameters = null;

    for (PsiTypeParameter typeParameter : owner.getTypeParameters()) {
      if (!hasTagForParameter(tags, typeParameter)) {
        (absentParameters = list(absentParameters)).add(typeParameter);
      }
    }

    if (absentParameters != null) {
      for (PsiTypeParameter typeParameter : absentParameters) {
        String name = typeParameter.getName();
        if (name != null) {
          String message = JavaBundle.message("inspection.javadoc.method.problem.missing.param.tag", "<code>&lt;" + name + "&gt;</code>");
          problem(holder, toHighlight, message, new JavaDocFixes.AddMissingTagFix("param", "<" + name + ">"));
        }
      }
    }
  }

  public static boolean hasTagForParameter(PsiDocTag @NotNull [] tags, PsiElement param) {
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName()) && tag.getValueElement() instanceof PsiDocParamRef ref) {
        PsiReference psiReference = ref.getReference();
        if (psiReference != null && psiReference.isReferenceTo(param)) {
          return true;
        }
      }
    }

    return false;
  }

  private static <T> List<T> list(List<T> list) {
    return list != null ? list : new SmartList<>();
  }
}

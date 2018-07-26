// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.javaDoc;

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInspection.*;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.notNull;

public class JavaDocLocalInspectionBase extends LocalInspectionTool {
  public static final String SHORT_NAME = "JavaDoc";

  protected static final String NONE = "none";
  protected static final String PACKAGE_LOCAL = "package";
  protected static final String PUBLIC = PsiModifier.PUBLIC;
  protected static final String PROTECTED = PsiModifier.PROTECTED;
  protected static final String PRIVATE = PsiModifier.PRIVATE;

  private static final String IGNORE_ACCESSORS_ATTR_NAME = "IGNORE_ACCESSORS";
  private static final String IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME = "IGNORE_DUPLICATED_THROWS_TAGS";
  private static final String MODULE_OPTIONS_TAG_NAME = "MODULE_OPTIONS";

  @SuppressWarnings("deprecation")
  public static class Options implements JDOMExternalizable {
    public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
    public String REQUIRED_TAGS = "";

    public Options() {}

    public Options(String accessJavadocRequiredFor, String requiredTags) {
      ACCESS_JAVADOC_REQUIRED_FOR = accessJavadocRequiredFor;
      REQUIRED_TAGS = requiredTags;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

    private boolean isModified() {
      return !(ACCESS_JAVADOC_REQUIRED_FOR.equals(NONE) && REQUIRED_TAGS.isEmpty());
    }
  }

  protected final Options PACKAGE_OPTIONS = new Options("none", "");
  protected final Options MODULE_OPTIONS = new Options("none", "");

  public Options TOP_LEVEL_CLASS_OPTIONS = new Options("none", "");
  public Options INNER_CLASS_OPTIONS = new Options("none", "");
  public Options METHOD_OPTIONS = new Options("none", "@return@param@throws or @exception");
  public Options FIELD_OPTIONS = new Options("none", "");
  public boolean IGNORE_DEPRECATED;
  public boolean IGNORE_JAVADOC_PERIOD = true;
  @Deprecated
  public boolean IGNORE_DUPLICATED_THROWS;
  public boolean IGNORE_POINT_TO_ITSELF;

  public String myAdditionalJavadocTags = "";

  private boolean myIgnoreDuplicatedThrows = true;
  private boolean myIgnoreEmptyDescriptions;
  private boolean myIgnoreSimpleAccessors;

  public void setPackageOption(String modifier, String tags) {
    PACKAGE_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = modifier;
    PACKAGE_OPTIONS.REQUIRED_TAGS = tags;
  }

  public void registerAdditionalTag(@NotNull String tag) {
    if (!myAdditionalJavadocTags.isEmpty()) {
      myAdditionalJavadocTags += "," + tag;
    }
    else {
      myAdditionalJavadocTags = tag;
    }
  }

  public boolean isIgnoreDuplicatedThrows() {
    return myIgnoreDuplicatedThrows;
  }

  public void setIgnoreDuplicatedThrows(boolean ignoreDuplicatedThrows) {
    myIgnoreDuplicatedThrows = ignoreDuplicatedThrows;
  }

  public void setIgnoreEmptyDescriptions(boolean ignoreEmptyDescriptions) {
    myIgnoreEmptyDescriptions = ignoreEmptyDescriptions;
  }

  public boolean isIgnoreSimpleAccessors() {
    return myIgnoreSimpleAccessors;
  }

  public void setIgnoreSimpleAccessors(boolean ignoreSimpleAccessors) {
    myIgnoreSimpleAccessors = ignoreSimpleAccessors;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);

    if (myIgnoreSimpleAccessors) {
      JDOMExternalizerUtil.writeCustomField(node, IGNORE_ACCESSORS_ATTR_NAME, String.valueOf(true));
    }

    if (!myIgnoreDuplicatedThrows) {
      JDOMExternalizerUtil.writeCustomField(node, IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME, String.valueOf(false));
    }

    if (MODULE_OPTIONS.isModified()) {
      MODULE_OPTIONS.writeExternal(JDOMExternalizerUtil.writeOption(node, MODULE_OPTIONS_TAG_NAME));
    }

    if (PACKAGE_OPTIONS.isModified()) {
      PACKAGE_OPTIONS.writeExternal(node);
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);

    String ignoreAccessors = JDOMExternalizerUtil.readCustomField(node, IGNORE_ACCESSORS_ATTR_NAME);
    if (ignoreAccessors != null) {
      myIgnoreSimpleAccessors = Boolean.parseBoolean(ignoreAccessors);
    }

    String ignoreDuplicatedThrows = JDOMExternalizerUtil.readCustomField(node, IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME);
    if (ignoreDuplicatedThrows != null) {
      myIgnoreDuplicatedThrows = Boolean.parseBoolean(ignoreDuplicatedThrows);
    }

    Element moduleOptions = JDOMExternalizerUtil.readOption(node, MODULE_OPTIONS_TAG_NAME);
    if (moduleOptions != null) {
      MODULE_OPTIONS.readExternal(moduleOptions);
    }

    PACKAGE_OPTIONS.readExternal(node);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkFile(file, holder, isOnTheFly);
        }
      }

      @Override
      public void visitModule(PsiJavaModule module) {
        checkModule(module, holder, isOnTheFly);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        checkClass(aClass, holder, isOnTheFly);
      }

      @Override
      public void visitField(PsiField field) {
        checkField(field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        checkMethod(method, holder, isOnTheFly);
      }
    };
  }

  private void checkFile(PsiJavaFile file, ProblemsHolder delegate, boolean isOnTheFly) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    if (pkg == null) return;

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    if (IGNORE_DEPRECATED && isDeprecated(pkg, docComment)) {
      return;
    }

    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, pkg);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);
    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();
      checkBasics(docComment, tags, pkg, required, PACKAGE_OPTIONS, holder);
    }
    else if (required) {
      PsiElement toHighlight = notNull(file.getPackageStatement(), file);
      JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
    }
  }

  private void checkModule(PsiJavaModule module, ProblemsHolder delegate, boolean isOnTheFly) {
    PsiDocComment docComment = module.getDocComment();
    if (IGNORE_DEPRECATED && isDeprecated(module, docComment)) {
      return;
    }

    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, module);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), module, required, MODULE_OPTIONS, holder);
    }
    else if (required) {
      JavadocHighlightUtil.reportMissingTag(module.getNameIdentifier(), holder);
    }
  }

  private void checkClass(PsiClass aClass, ProblemsHolder delegate, boolean isOnTheFly) {
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiSyntheticClass || aClass instanceof PsiTypeParameter) {
      return;
    }
    if (IGNORE_DEPRECATED && aClass.isDeprecated()) {
      return;
    }

    PsiDocComment docComment = aClass.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, aClass);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();

      Options options = ClassUtil.isTopLevelClass(aClass) ? TOP_LEVEL_CLASS_OPTIONS : INNER_CLASS_OPTIONS;
      checkBasics(docComment, tags, aClass, required, options, holder);

      if (required && isTagRequired(options, "param")) {
        JavadocHighlightUtil.checkMissingTypeParamTags(aClass, tags, docComment.getFirstChild(), holder);
      }
    }
    else if (required) {
      PsiElement toHighlight = notNull(aClass.getNameIdentifier(), aClass);
      JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
    }
  }

  private void checkField(PsiField field, ProblemsHolder delegate, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && isDeprecated(field)) {
      return;
    }

    PsiDocComment docComment = field.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, field);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), field, required, FIELD_OPTIONS, holder);
    }
    else if (required) {
      JavadocHighlightUtil.reportMissingTag(field.getNameIdentifier(), holder);
    }
  }

  private void checkMethod(PsiMethod method, ProblemsHolder delegate, boolean isOnTheFly) {
    if (method instanceof SyntheticElement) {
      return;
    }
    if (IGNORE_DEPRECATED && isDeprecated(method)) {
      return;
    }
    if (myIgnoreSimpleAccessors && PropertyUtilBase.isSimplePropertyAccessor(method)) {
      return;
    }

    PsiDocComment docComment = method.getDocComment();
    boolean hasSupers = method.findSuperMethods().length > 0;
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, method);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      if (!isInherited(docComment, method)) {
        PsiDocTag[] tags = docComment.getTags();

        if (required && !hasSupers) {
          if (isTagRequired(METHOD_OPTIONS, "return")) {
            JavadocHighlightUtil.checkMissingReturnTag(tags, method, docComment.getFirstChild(), holder);
          }
          if (isTagRequired(METHOD_OPTIONS, "param")) {
            JavadocHighlightUtil.checkMissingParamTags(tags, method, docComment.getFirstChild(), holder);
            JavadocHighlightUtil.checkMissingTypeParamTags(method, tags, docComment.getFirstChild(), holder);
          }
          if (isTagRequired(METHOD_OPTIONS, "throws")) {
            JavadocHighlightUtil.checkMissingThrowsTags(tags, method, docComment.getFirstChild(), holder);
          }
        }

        if (!myIgnoreEmptyDescriptions) {
          JavadocHighlightUtil.checkEmptyMethodTagsDescription(tags, holder);
        }

        checkBasics(docComment, tags, method, false, METHOD_OPTIONS, holder);
      }
    }
    else if (required && !hasSupers) {
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) {
        ExtensionPoint<Condition<PsiMember>> ep = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.JAVADOC_LOCAL);
        if (Stream.of(ep.getExtensions()).noneMatch(condition -> condition.value(method))) {
          JavadocHighlightUtil.reportMissingTag(nameIdentifier, holder);
        }
      }
    }
  }

  private void checkBasics(PsiDocComment docComment, PsiDocTag[] tags, PsiElement context, boolean required, Options options, ProblemHolderImpl holder) {
    if (required) {
      JavadocHighlightUtil.checkRequiredTags(tags, options, docComment.getFirstChild(), holder);
    }

    JavadocHighlightUtil.checkRequiredTagDescriptions(tags, holder);

    JavadocHighlightUtil.checkTagValues(tags, context, holder);

    if (!IGNORE_JAVADOC_PERIOD) {
      JavadocHighlightUtil.checkForPeriod(docComment, context, holder);
    }

    JavadocHighlightUtil.checkInlineTags(docComment.getDescriptionElements(), holder);

    JavadocHighlightUtil.checkForBadCharacters(docComment, holder);

    JavadocHighlightUtil.checkDuplicateTags(tags, holder);
  }

  private static boolean isDeprecated(PsiModifierListOwner element, PsiDocComment docComment) {
    return PsiImplUtil.isDeprecatedByAnnotation(element) || docComment != null && docComment.findTagByName("deprecated") != null;
  }

  protected static boolean isTagRequired(Options options, String tag) {
    return options.REQUIRED_TAGS.contains(tag);
  }

  private static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated() || element.getContainingClass() != null && element.getContainingClass().isDeprecated();
  }

  private static boolean isInherited(PsiDocComment docComment, PsiMethod psiMethod) {
    for (PsiElement descriptionElement : docComment.getDescriptionElements()) {
      if (descriptionElement instanceof PsiInlineDocTag && "inheritDoc".equals(((PsiInlineDocTag)descriptionElement).getName())) {
        return true;
      }
    }

    if (docComment.findTagByName("inheritDoc") != null) {
      JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(psiMethod.getProject()).getTagInfo("inheritDoc");
      if (tagInfo != null && tagInfo.isValidInContext(psiMethod)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.javadoc.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.javadoc.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "javadoc";
  }

  protected LocalQuickFix createAddJavadocFix(@NotNull PsiElement nameIdentifier, boolean isOnTheFly) {
    return null;
  }

  protected LocalQuickFix createAddMissingTagFix(@NotNull String tag, @NotNull String value, boolean isOnTheFly) {
    return null;
  }

  protected LocalQuickFix createAddMissingParamTagFix(@NotNull String name, boolean isOnTheFly) {
    return null;
  }

  protected LocalQuickFix createRegisterTagFix(@NotNull String tag, boolean isOnTheFly) {
    return null;
  }

  private class ProblemHolderImpl implements JavadocHighlightUtil.ProblemHolder {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;

    private ProblemHolderImpl(ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override
    public Project project() {
      return myHolder.getManager().getProject();
    }

    @Override
    public JavaDocLocalInspectionBase inspection() {
      return JavaDocLocalInspectionBase.this;
    }

    @Override
    public void problem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(
        toHighlight, message, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
    }

    @Override
    public void eolProblem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      LocalQuickFix[] fixes = fix != null ? new LocalQuickFix[]{fix} : null;
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(
        toHighlight, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, true));
    }

    @Override
    public LocalQuickFix addJavadocFix(@NotNull PsiElement nameIdentifier) {
      return createAddJavadocFix(nameIdentifier, myOnTheFly);
    }

    @Override
    public LocalQuickFix addMissingTagFix(@NotNull String tag, @NotNull String value) {
      return createAddMissingTagFix(tag, value, myOnTheFly);
    }

    @Override
    public LocalQuickFix addMissingParamTagFix(@NotNull String name) {
      return createAddMissingParamTagFix(name, myOnTheFly);
    }

    @Override
    public LocalQuickFix registerTagFix(@NotNull String tag) {
      return createRegisterTagFix(tag, myOnTheFly);
    }
  }
}
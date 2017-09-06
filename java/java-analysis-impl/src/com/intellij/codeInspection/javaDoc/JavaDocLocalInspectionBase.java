/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.javaDoc;

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JavaDocLocalInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public static final String SHORT_NAME = "JavaDoc";

  protected static final String NONE = "none";
  protected static final String PACKAGE_LOCAL = "package";
  protected static final String PUBLIC = PsiModifier.PUBLIC;
  protected static final String PROTECTED = PsiModifier.PROTECTED;
  protected static final String PRIVATE = PsiModifier.PRIVATE;

  private static final String IGNORE_ACCESSORS_ATTR_NAME = "IGNORE_ACCESSORS";
  private static final String IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME = "IGNORE_DUPLICATED_THROWS_TAGS";

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
  }

  protected final Options PACKAGE_OPTIONS = new Options("none", "");

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
      node.addContent(new Element(IGNORE_ACCESSORS_ATTR_NAME).setAttribute("value", String.valueOf(true)));
    }
    if (!myIgnoreDuplicatedThrows) {
      node.addContent(new Element(IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME).setAttribute("value", String.valueOf(false)));
    }
    if (!PACKAGE_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR.equals("none") || !PACKAGE_OPTIONS.REQUIRED_TAGS.isEmpty()) {
      PACKAGE_OPTIONS.writeExternal(node);
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    Element ignoreAccessorsTag = node.getChild(IGNORE_ACCESSORS_ATTR_NAME);
    if (ignoreAccessorsTag != null) {
      myIgnoreSimpleAccessors = Boolean.parseBoolean(ignoreAccessorsTag.getAttributeValue("value"));
    }
    Element ignoreDupThrowsTag = node.getChild(IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME);
    if (ignoreDupThrowsTag != null) {
      myIgnoreDuplicatedThrows = Boolean.parseBoolean(ignoreDupThrowsTag.getAttributeValue("value"));
    }
    PACKAGE_OPTIONS.readExternal(node);
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(file.getName()) || !(file instanceof PsiJavaFile)) {
      return null;
    }

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    boolean required = aPackage != null && JavadocHighlightUtil.isJavaDocRequired(this, aPackage);
    ProblemHolderImpl holder = new ProblemHolderImpl(manager, isOnTheFly);

    if (IGNORE_DEPRECATED &&
        (AnnotationUtil.findAnnotation(aPackage, CommonClassNames.JAVA_LANG_DEPRECATED) != null ||
         docComment != null && docComment.findTagByName("deprecated") != null)) {
      return null;
    }

    if (docComment == null) {
      if (required) {
        PsiElement toHighlight = ObjectUtils.notNull(((PsiJavaFile)file).getPackageStatement(), file);
        JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
      }
    }
    else {
      PsiDocTag[] tags = docComment.getTags();

      if (required) {
        Predicate<String> tagChecker = tag -> isTagRequired(aPackage, tag);
        JavadocHighlightUtil.checkRequiredTags(tags, tagChecker, docComment.getFirstChild(), holder);
      }

      JavadocHighlightUtil.checkRequiredTagDescriptions(tags, holder);

      JavadocHighlightUtil.checkTagValues(tags, aPackage, holder);

      JavadocHighlightUtil.checkInlineTags(docComment.getDescriptionElements(), holder);

      if (!IGNORE_JAVADOC_PERIOD) {
        JavadocHighlightUtil.checkForPeriod(docComment, aPackage, holder);
      }

      JavadocHighlightUtil.checkForBadCharacters(docComment, holder);
    }

    return holder.problems();
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiClass instanceof PsiAnonymousClass || psiClass instanceof PsiSyntheticClass || psiClass instanceof PsiTypeParameter) {
      return null;
    }
    if (IGNORE_DEPRECATED && psiClass.isDeprecated()) {
      return null;
    }

    PsiDocComment docComment = psiClass.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, psiClass);
    ProblemHolderImpl holder = new ProblemHolderImpl(manager, isOnTheFly);

    if (docComment == null) {
      if (required) {
        PsiElement toHighlight = ObjectUtils.notNull(psiClass.getNameIdentifier(), psiClass);
        JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
      }
    }
    else {
      PsiDocTag[] tags = docComment.getTags();

      if (required) {
        Predicate<String> tagChecker = tag -> isTagRequired(psiClass, tag);
        JavadocHighlightUtil.checkRequiredTags(tags, tagChecker, docComment.getFirstChild(), holder);
      }

      JavadocHighlightUtil.checkRequiredTagDescriptions(tags, holder);

      JavadocHighlightUtil.checkTagValues(tags, psiClass, holder);

      if (!IGNORE_JAVADOC_PERIOD) {
        JavadocHighlightUtil.checkForPeriod(docComment, psiClass, holder);
      }

      JavadocHighlightUtil.checkInlineTags(docComment.getDescriptionElements(), holder);

      JavadocHighlightUtil.checkForBadCharacters(docComment, holder);

      JavadocHighlightUtil.checkDuplicateTags(tags, holder);

      if (required && isTagRequired(psiClass, "param")) {
        JavadocHighlightUtil.checkMissingTypeParamTags(psiClass, tags, docComment.getFirstChild(), holder);
      }
    }

    return holder.problems();
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField psiField, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && isDeprecated(psiField)) {
      return null;
    }

    PsiDocComment docComment = psiField.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, psiField);
    ProblemHolderImpl holder = new ProblemHolderImpl(manager, isOnTheFly);

    if (docComment == null) {
      if (required) {
        JavadocHighlightUtil.reportMissingTag(psiField.getNameIdentifier(), holder);
      }
    }
    else {
      JavadocHighlightUtil.checkTagValues(docComment.getTags(), psiField, holder);

      JavadocHighlightUtil.checkInlineTags(docComment.getDescriptionElements(), holder);

      if (!IGNORE_JAVADOC_PERIOD) {
        JavadocHighlightUtil.checkForPeriod(docComment, psiField, holder);
      }

      JavadocHighlightUtil.checkDuplicateTags(docComment.getTags(), holder);

      JavadocHighlightUtil.checkForBadCharacters(docComment, holder);
    }

    return holder.problems();
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiMethod instanceof SyntheticElement) {
      return null;
    }
    if (IGNORE_DEPRECATED && isDeprecated(psiMethod)) {
      return null;
    }
    if (myIgnoreSimpleAccessors && PropertyUtil.isSimplePropertyAccessor(psiMethod)) {
      return null;
    }

    PsiDocComment docComment = psiMethod.getDocComment();
    boolean hasSupers = psiMethod.findSuperMethods().length > 0;
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, psiMethod);
    ProblemHolderImpl holder = new ProblemHolderImpl(manager, isOnTheFly);

    if (docComment == null) {
      if (!required || hasSupers) {
        return null;
      }

      PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
      if (nameIdentifier == null) {
        return null;
      }

      ExtensionPoint<Condition<PsiMember>> ep = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.JAVADOC_LOCAL);
      if (Stream.of(ep.getExtensions()).anyMatch(condition -> condition.value(psiMethod))) {
        return null;
      }

      JavadocHighlightUtil.reportMissingTag(nameIdentifier, holder);
    }
    else {
      PsiElement[] descriptionElements = docComment.getDescriptionElements();
      if (isInherited(docComment, descriptionElements, psiMethod)) {
        return null;
      }

      JavadocHighlightUtil.checkInlineTags(descriptionElements, holder);

      PsiDocTag[] tags = docComment.getTags();
      if (required && !hasSupers) {
        if (isTagRequired(psiMethod, "return")) {
          JavadocHighlightUtil.checkMissingReturnTag(tags, psiMethod, docComment.getFirstChild(), holder);
        }
        if (isTagRequired(psiMethod, "param")) {
          JavadocHighlightUtil.checkMissingParamTags(tags, psiMethod, docComment.getFirstChild(), holder);
        }
        if (isTagRequired(psiMethod, "throws")) {
          JavadocHighlightUtil.checkMissingThrowsTags(tags, psiMethod, docComment.getFirstChild(), holder);
        }
      }

      if (!myIgnoreEmptyDescriptions) {
        JavadocHighlightUtil.checkEmptyMethodTagsDescription(tags, holder);
      }

      JavadocHighlightUtil.checkTagValues(tags, psiMethod, holder);

      if (!IGNORE_JAVADOC_PERIOD) {
        JavadocHighlightUtil.checkForPeriod(docComment, psiMethod, holder);
      }

      JavadocHighlightUtil.checkForBadCharacters(docComment, holder);

      JavadocHighlightUtil.checkDuplicateTags(tags, holder);
    }

    return holder.problems();
  }

  private boolean isTagRequired(PsiElement context, String tag) {
    if (context instanceof PsiPackage) {
      return isTagRequired(PACKAGE_OPTIONS, tag);
    }

    if (context instanceof PsiClass) {
      boolean isInner = PsiTreeUtil.getParentOfType(context, PsiClass.class) != null;
      return isTagRequired(isInner ? INNER_CLASS_OPTIONS : TOP_LEVEL_CLASS_OPTIONS, tag);
    }

    if (context instanceof PsiMethod) {
      return isTagRequired(METHOD_OPTIONS, tag);
    }

    if (context instanceof PsiField) {
      return isTagRequired(FIELD_OPTIONS, tag);
    }

    return false;
  }

  protected static boolean isTagRequired(Options options, String tag) {
    return options.REQUIRED_TAGS.contains(tag);
  }

  private static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated() || element.getContainingClass() != null && element.getContainingClass().isDeprecated();
  }

  private static boolean isInherited(PsiDocComment docComment, PsiElement[] descriptionElements, PsiMethod psiMethod) {
    for (PsiElement descriptionElement : descriptionElements) {
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
    private final InspectionManager myManager;
    private final boolean myOnTheFly;
    private List<ProblemDescriptor> myProblems;

    private ProblemHolderImpl(InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
    }

    public ProblemDescriptor[] problems() {
      return myProblems == null || myProblems.isEmpty() ? null : myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
    }

    @Override
    public Project project() {
      return myManager.getProject();
    }

    @Override
    public JavaDocLocalInspectionBase inspection() {
      return JavaDocLocalInspectionBase.this;
    }

    @Override
    public void problem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      if (myProblems == null) myProblems = ContainerUtil.newSmartList();
      myProblems.add(myManager.createProblemDescriptor(toHighlight, message, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
    }

    @Override
    public void eolProblem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      if (myProblems == null) myProblems = ContainerUtil.newSmartList();
      LocalQuickFix[] fixes = fix != null ? new LocalQuickFix[]{fix} : null;
      myProblems.add(myManager.createProblemDescriptor(toHighlight, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, true));
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
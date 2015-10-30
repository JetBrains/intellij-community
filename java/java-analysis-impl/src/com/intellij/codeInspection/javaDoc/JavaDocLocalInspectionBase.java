/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaDocLocalInspectionBase extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.javaDoc.JavaDocLocalInspectionBase");
  @NonNls protected static final String NONE = "none";
  @NonNls protected static final String PUBLIC = "public";
  @NonNls protected static final String PROTECTED = "protected";
  @NonNls protected static final String PACKAGE_LOCAL = "package";
  @NonNls protected static final String PRIVATE = "private";

  private static final String REQUIRED_JAVADOC_IS_ABSENT = InspectionsBundle.message("inspection.javadoc.problem.descriptor");

  @NonNls private static final Set<String> ourUniqueTags = new HashSet<String>();
  @NonNls public static final String SHORT_NAME = "JavaDoc";

  static {
    ourUniqueTags.add("return");
    ourUniqueTags.add("deprecated");
    ourUniqueTags.add("serial");
    ourUniqueTags.add("serialData");
  }

  @NonNls private static final String IGNORE_ACCESSORS_ATTR_NAME = "IGNORE_ACCESSORS";
  @NonNls private static final String IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME = "IGNORE_DUPLICATED_THROWS_TAGS";

  public static class Options implements JDOMExternalizable {
    @NonNls public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
    @NonNls public String REQUIRED_TAGS = "";

    public Options() {}

    public Options(String ACCESS_JAVADOC_REQUIRED_FOR, String REQUIRED_TAGS) {
      this.ACCESS_JAVADOC_REQUIRED_FOR = ACCESS_JAVADOC_REQUIRED_FOR;
      this.REQUIRED_TAGS = REQUIRED_TAGS;
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

  @NonNls protected final Options PACKAGE_OPTIONS = new Options("none", "");
  @NonNls public Options TOP_LEVEL_CLASS_OPTIONS = new Options("none", "");
  @NonNls public Options INNER_CLASS_OPTIONS = new Options("none", "");
  @NonNls public Options METHOD_OPTIONS = new Options("none", "@return@param@throws or @exception");
  @NonNls public Options FIELD_OPTIONS = new Options("none", "");
  public boolean IGNORE_DEPRECATED = false;
  public boolean IGNORE_JAVADOC_PERIOD = true;
  @SuppressWarnings("unused") @Deprecated
  public boolean IGNORE_DUPLICATED_THROWS = false;

  private boolean myIgnoreDuplicatedThrows = true;

  public boolean getIgnoreDuplicatedThrows() {
    return myIgnoreDuplicatedThrows;
  }

  public void setIgnoreDuplicatedThrows(boolean ignoreDuplicatedThrows) {
    myIgnoreDuplicatedThrows = ignoreDuplicatedThrows;
  }

  public boolean IGNORE_POINT_TO_ITSELF = false;
  public String myAdditionalJavadocTags = "";

  private boolean myIgnoreEmptyDescriptions = false;
  protected boolean myIgnoreSimpleAccessors = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public void setIgnoreSimpleAccessors(boolean ignoreSimpleAccessors) {
    myIgnoreSimpleAccessors = ignoreSimpleAccessors;
  }

  public void setPackageOption(@NonNls String modifier, @NonNls String tags) {
    PACKAGE_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = modifier;
    PACKAGE_OPTIONS.REQUIRED_TAGS = tags;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (myIgnoreSimpleAccessors) {
      final Element option = new Element(IGNORE_ACCESSORS_ATTR_NAME);
      option.setAttribute("value", String.valueOf(true));
      node.addContent(option);
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
    final Element ignoreAccessorsTag = node.getChild(IGNORE_ACCESSORS_ATTR_NAME);
    if (ignoreAccessorsTag != null) {
      myIgnoreSimpleAccessors = Boolean.parseBoolean(ignoreAccessorsTag.getAttributeValue("value"));
    }
    Element ignoreDupThrowsTag = node.getChild(IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME);
    if (ignoreDupThrowsTag != null) {
      myIgnoreDuplicatedThrows = Boolean.parseBoolean(ignoreDupThrowsTag.getAttributeValue("value"));
    }
    PACKAGE_OPTIONS.readExternal(node);
  }

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager,
                                                    boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, onTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, @NotNull LocalQuickFix fix,
                                                    InspectionManager manager, boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly);
  }

  private static class AddMissingTagFix implements LocalQuickFix {
    private final String myTag;
    private final String myValue;

    public AddMissingTagFix(@NonNls @NotNull String tag, @NotNull String value) {
      myTag = tag;
      myValue = value;
    }
    public AddMissingTagFix(@NotNull String tag) {
      this(tag, "");
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      try {
        final PsiDocComment docComment = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocComment.class);
        if (docComment != null) {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(docComment)) return;
          final PsiDocTag tag = factory.createDocTagFromText("@" + myTag + " " + myValue);
          PsiElement addedTag;
          final PsiElement anchor = getAnchor(descriptor);
          if (anchor != null) {
            addedTag = docComment.addBefore(tag, anchor);
          }
          else {
            addedTag = docComment.add(tag);
          }
          moveCaretAfter(addedTag);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      return null;
    }

    private static void moveCaretAfter(final PsiElement newCaretPosition) {
      PsiElement sibling = newCaretPosition.getNextSibling();
      if (sibling != null) {
        ((Navigatable)sibling).navigate(true);
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(file.getName()) || !(file instanceof PsiJavaFile)) {
      return null;
    }
    final PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    final JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
    final PsiDirectory directory = file.getContainingDirectory();
    final PsiPackage aPackage = directoryService.getPackage(directory);
    if (IGNORE_DEPRECATED && aPackage != null) {
      final PsiModifierList modifierList = aPackage.getModifierList();
      if (modifierList != null && modifierList.findAnnotation("java.lang.Deprecated") != null) {
        return null;
      }
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiPackageStatement packageStatement = javaFile.getPackageStatement();
    final PsiElement elementToHighlight = packageStatement != null ? packageStatement : file;

    final boolean required = aPackage != null && isJavaDocRequired(aPackage);
    if (docComment != null) {
      if (IGNORE_DEPRECATED && docComment.findTagByName("deprecated") != null) {
        return null;
      }
    }
    else {
      return required
             ? new ProblemDescriptor[]{createRequiredJavadocAbsentDescription(elementToHighlight, manager, isOnTheFly)}
             : null;
    }

    final PsiDocTag[] tags = docComment.getTags();
    final List<ProblemDescriptor> problems =
      getRequiredTagProblems(aPackage, docComment.getFirstChild(), tags, manager, isOnTheFly, required);
    final List<ProblemDescriptor> tagProblems = getTagValuesProblems(aPackage, tags, manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }
    checkInlineTags(manager, problems, docComment.getDescriptionElements(),
                    JavadocManager.SERVICE.getInstance(docComment.getProject()), isOnTheFly);
    checkForPeriodInDoc(aPackage, docComment, problems, manager, isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiClass instanceof PsiAnonymousClass) return null;
    if (psiClass instanceof PsiSyntheticClass) return null;
    if (psiClass instanceof PsiTypeParameter) return null;
    if (IGNORE_DEPRECATED && psiClass.isDeprecated()) {
      return null;
    }
    PsiDocComment docComment = psiClass.getDocComment();
    final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    final PsiElement elementToHighlight = nameIdentifier != null ? nameIdentifier : psiClass;
    final boolean required = isJavaDocRequired(psiClass);
    if (docComment == null) {
      return required
             ? new ProblemDescriptor[]{createRequiredJavadocAbsentDescription(elementToHighlight, manager, isOnTheFly)}
             : null;
    }

    PsiDocTag[] tags = docComment.getTags();
    final List<ProblemDescriptor> problems = getRequiredTagProblems(psiClass, docComment.getFirstChild(), tags, manager, isOnTheFly, required);

    List<ProblemDescriptor> tagProblems = getTagValuesProblems(psiClass, tags, manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }
    checkForPeriodInDoc(psiClass, docComment, problems, manager, isOnTheFly);
    checkInlineTags(manager, problems, docComment.getDescriptionElements(),
                    JavadocManager.SERVICE.getInstance(docComment.getProject()), isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    checkDuplicateTags(tags, problems, manager, isOnTheFly);

    if (required && isTagRequired(psiClass, "param") && psiClass.hasTypeParameters() && nameIdentifier != null) {
      ArrayList<PsiTypeParameter> absentParameters = null;
      final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      for (PsiTypeParameter typeParameter : typeParameters) {
        if (!isFound(tags, typeParameter)) {
          if (absentParameters == null) absentParameters = new ArrayList<PsiTypeParameter>(1);
          absentParameters.add(typeParameter);
        }
      }
      if (absentParameters != null) {
        for (PsiTypeParameter psiTypeParameter : absentParameters) {
          problems.add(createMissingParamTagDescriptor(docComment.getFirstChild(), psiTypeParameter, manager, isOnTheFly));
        }
      }
    }

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private List<ProblemDescriptor> getRequiredTagProblems(PsiElement context,
                                                         PsiElement elementToHighlight,
                                                         PsiDocTag[] tags,
                                                         InspectionManager manager, boolean isOnTheFly, boolean required) {
    @NonNls String[] tagsToCheck = {"author", "version", "since"};
    @NonNls String[] absentDescriptionKeys = {
      "inspection.javadoc.problem.missing.author.description",
      "inspection.javadoc.problem.missing.version.description",
      "inspection.javadoc.problem.missing.since.description"};
    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    if (required) {
      boolean[] isTagRequired = new boolean[tagsToCheck.length];
      boolean[] isTagPresent = new boolean[tagsToCheck.length];

      boolean someTagsAreRequired = false;
      for (int i = 0; i < tagsToCheck.length; i++) {
        final String tag = tagsToCheck[i];
        someTagsAreRequired |= isTagRequired[i] = isTagRequired(context, tag);
      }

      if (someTagsAreRequired) {
        for (PsiDocTag tag : tags) {
          String tagName = tag.getName();
          for (int i = 0; i < tagsToCheck.length; i++) {
            final String tagToCheck = tagsToCheck[i];
            if (tagToCheck.equals(tagName)) {
              isTagPresent[i] = true;
            }
          }
        }
      }

      for (int i = 0; i < tagsToCheck.length; i++) {
        final String tagToCheck = tagsToCheck[i];
        if (isTagRequired[i] && !isTagPresent[i]) {
          problems.add(createMissingTagDescriptor(elementToHighlight, tagToCheck, manager, isOnTheFly));
        }
      }
    }
    for (PsiDocTag tag : tags) {
      for (int i = 0; i < tagsToCheck.length; i++) {
        final String tagToCheck = tagsToCheck[i];
        if (tagToCheck.equals(tag.getName()) && extractTagDescription(tag).isEmpty()) {
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message(absentDescriptionKeys[i]), manager, isOnTheFly));
        }
      }
    }
    return problems;
  }

  private static ProblemDescriptor createMissingParamTagDescriptor(final PsiElement elementToHighlight,
                                                                   final PsiTypeParameter psiTypeParameter,
                                                                   final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@param</code>");
    return createDescriptor(elementToHighlight, message, new AddMissingTagFix("param", "<" + psiTypeParameter.getName() + ">"), manager,
                            isOnTheFly);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField psiField, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && (psiField.isDeprecated() || psiField.getContainingClass().isDeprecated())) {
      return null;
    }

    PsiDocComment docComment = psiField.getDocComment();
    if (docComment == null) {
      final PsiIdentifier nameIdentifier = psiField.getNameIdentifier();
      return isJavaDocRequired(psiField)
             ? new ProblemDescriptor[]{createRequiredJavadocAbsentDescription(nameIdentifier, manager, isOnTheFly)}
             : null;
    }

    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiField, docComment.getTags(), manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }
    checkInlineTags(manager, problems, docComment.getDescriptionElements(),
                    JavadocManager.SERVICE.getInstance(docComment.getProject()), isOnTheFly);
    checkForPeriodInDoc(psiField, docComment, problems, manager, isOnTheFly);
    checkDuplicateTags(docComment.getTags(), problems, manager, isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private ProblemDescriptor createRequiredJavadocAbsentDescription(@NotNull PsiElement nameIdentifier,
                                                                   @NotNull InspectionManager manager,
                                                                   boolean isOnTheFly) {
    LocalQuickFix fix = createAddJavadocFix(nameIdentifier, isOnTheFly);
    return fix != null ? 
           createDescriptor(nameIdentifier, REQUIRED_JAVADOC_IS_ABSENT, fix, manager, isOnTheFly):
           createDescriptor(nameIdentifier, REQUIRED_JAVADOC_IS_ABSENT, manager, isOnTheFly);
  }

  protected LocalQuickFix createAddJavadocFix(@NotNull PsiElement nameIdentifier, boolean isOnTheFly) {
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiMethod instanceof SyntheticElement) return null;
    if (IGNORE_DEPRECATED && (psiMethod.isDeprecated() || psiMethod.getContainingClass().isDeprecated())) {
      return null;
    }
    if (myIgnoreSimpleAccessors && PropertyUtil.isSimplePropertyAccessor(psiMethod)) {
      return null;
    }
    PsiDocComment docComment = psiMethod.getDocComment();
    final PsiMethod[] superMethods = psiMethod.findSuperMethods();
    final boolean required = isJavaDocRequired(psiMethod);
    if (docComment == null) {
      if (required) {
        if (superMethods.length > 0) return null;
        ExtensionPoint<Condition<PsiMember>> point = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.JAVADOC_LOCAL);
        for (Condition<PsiMember> addIn : point.getExtensions()) {
          if (addIn.value(psiMethod)) return null;
        }
        if (superMethods.length == 0) {
          final PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
          return nameIdentifier != null ? new ProblemDescriptor[] {
            createRequiredJavadocAbsentDescription(nameIdentifier, manager, isOnTheFly)} : null;
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
    }

    final PsiElement[] descriptionElements = docComment.getDescriptionElements();
    for (PsiElement descriptionElement : descriptionElements) {
      if (descriptionElement instanceof PsiInlineDocTag) {
        if ("inheritDoc".equals(((PsiInlineDocTag)descriptionElement).getName())) return null;
      }
    }

    List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);

    checkInlineTags(manager, problems, descriptionElements, JavadocManager.SERVICE.getInstance(docComment.getProject()), isOnTheFly);

    final PsiDocTag tagByName = docComment.findTagByName("inheritDoc");
    if (tagByName != null) {
      final String tagName = tagByName.getName();
      final JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(tagByName.getProject()).getTagInfo(tagName);
      if (tagInfo != null && tagInfo.isValidInContext(psiMethod)){
        return null;
      }
    }

    PsiDocTag[] tags = docComment.getTags();

    boolean isReturnRequired = false;
    boolean isReturnAbsent = true;
    if (superMethods.length == 0 && !psiMethod.isConstructor() &&
        !PsiType.VOID.equals(psiMethod.getReturnType()) && isTagRequired(psiMethod, "return")) {
      isReturnRequired = true;
      for (PsiDocTag tag : tags) {
        if ("return".equals(tag.getName())) {
          isReturnAbsent = false;
          break;
        }
      }
    }

    ArrayList<PsiParameter> absentParameters = null;
    if (required && superMethods.length == 0 && isTagRequired(psiMethod, "param") ) {
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      for (PsiParameter param : params) {
        if (!isFound(tags, param)) {
          if (absentParameters == null) absentParameters = new ArrayList<PsiParameter>(2);
          absentParameters.add(param);
        }
      }
    }



    if (required && isReturnRequired && isReturnAbsent) {
      problems.add(createMissingTagDescriptor(docComment.getFirstChild(), "return", manager, isOnTheFly));
    }

    if (absentParameters != null) {
      for (PsiParameter psiParameter : absentParameters) {
        problems.add(createMissingParamTagDescriptor(docComment.getFirstChild(), psiParameter, manager, isOnTheFly));
      }
    }

    if (!myIgnoreEmptyDescriptions) {
      for (PsiDocTag tag : tags) {
        if ("param".equals(tag.getName())) {
          final PsiElement[] dataElements = tag.getDataElements();
          final PsiDocTagValue valueElement = tag.getValueElement();
          boolean hasProblemsWithTag = dataElements.length < 2;
          if (!hasProblemsWithTag) {
            final StringBuilder buf = new StringBuilder();
            for (PsiElement element : dataElements) {
              if (element != valueElement){
                buf.append(element.getText());
              }
            }
            hasProblemsWithTag = buf.toString().trim().isEmpty();
          }
          if (hasProblemsWithTag) {
            if (valueElement != null) {
              problems.add(createDescriptor(valueElement,
                                            InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>@param " + valueElement.getText() + "</code>"),
                                            manager, isOnTheFly));
            }

          }
        }
      }
    }

    if (required && superMethods.length == 0 && isTagRequired(psiMethod, "@throws") && psiMethod.getThrowsList().getReferencedTypes().length > 0) {
      final Map<PsiClassType, PsiClass> declaredExceptions = new LinkedHashMap<PsiClassType, PsiClass>();
      final PsiClassType[] classTypes = psiMethod.getThrowsList().getReferencedTypes();
      for (PsiClassType classType : classTypes) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null){
          declaredExceptions.put(classType, psiClass);
        }
      }
      processThrowsTags(tags, declaredExceptions, manager, problems, isOnTheFly);
      if (!declaredExceptions.isEmpty()) {
        for (PsiClassType declaredException : declaredExceptions.keySet()) {
          problems.add(createMissingThrowsTagDescriptor(docComment.getFirstChild(), manager, declaredException, isOnTheFly));
        }
      }
    }

    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiMethod, tags, manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }

    checkForPeriodInDoc(psiMethod, docComment, problems, manager, isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        if (extractTagDescription(tag).isEmpty()) {
          PsiDocTagValue value = tag.getValueElement();
          if (value instanceof PsiDocParamRef) {
            PsiDocParamRef paramRef = (PsiDocParamRef)value;
            PsiParameter[] params = psiMethod.getParameterList().getParameters();
            for (PsiParameter param : params) {
              if (paramRef.getReference().isReferenceTo(param)) {
                problems.add(createDescriptor(value,
                                              InspectionsBundle.message("inspection.javadoc.method.problem.descriptor", "<code>@param</code>", "<code>" + param.getName() + "</code>"),
                                              manager, isOnTheFly));
              }
            }
          }
        }
      }
      else
        if ("return".equals(tag.getName()) && !myIgnoreEmptyDescriptions) {
          if (extractTagDescription(tag).isEmpty()) {
            String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>@return</code>");
            ProblemDescriptor descriptor = manager.createProblemDescriptor(tag.getNameElement(), message, (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                           isOnTheFly);
            problems.add(descriptor);
          }
        }
    }

    checkDuplicateTags(tags, problems, manager, isOnTheFly);

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public static boolean isFound(final PsiDocTag[] tags, final PsiElement param) {
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiDocParamRef paramRef = (PsiDocParamRef)value;
          final PsiReference psiReference = paramRef.getReference();
          if (psiReference != null && psiReference.isReferenceTo(param)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void processThrowsTags(@NotNull PsiDocTag[] tags,
                                 @NotNull Map<PsiClassType, PsiClass> declaredExceptions,
                                 @NotNull InspectionManager manager,
                                 @NotNull final List<ProblemDescriptor> problems,
                                 boolean isOnTheFly) {
    for (PsiDocTag tag : tags) {
      if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        final PsiDocTagValue value = tag.getValueElement();
        if (value == null) continue;
        final PsiElement firstChild = value.getFirstChild();
        if (firstChild == null) continue;
        final PsiElement psiElement = firstChild.getFirstChild();
        if (!(psiElement instanceof PsiJavaCodeReferenceElement)) continue;
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)psiElement;
        final PsiElement element = ref.resolve();
        if (element instanceof PsiClass){
          final PsiClass exceptionClass = (PsiClass)element;
          for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext();) {
            PsiClassType classType = it.next();
            final PsiClass psiClass = declaredExceptions.get(classType);
            if (InheritanceUtil.isInheritorOrSelf(exceptionClass, psiClass, true)) {
              if (!myIgnoreEmptyDescriptions && extractThrowsTagDescription(tag).isEmpty()) {
                problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>" + tag.getName() + "</code>"), manager,
                                              isOnTheFly));
              }
              it.remove();
            }
          }
        }
      }
    }
  }

  @Nullable
  private static ProblemDescriptor createMissingThrowsTagDescriptor(final PsiElement elementToHighlight,
                                                                    final InspectionManager manager,
                                                                    final PsiClassType exceptionClassType, boolean isOnTheFly) {
    @NonNls String tag = "throws";
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code> " + exceptionClassType.getCanonicalText());
    final String firstDeclaredException = exceptionClassType.getCanonicalText();
    return createDescriptor(elementToHighlight, message, new AddMissingTagFix(tag, firstDeclaredException), manager, isOnTheFly);
  }

  private static ProblemDescriptor createMissingTagDescriptor(PsiElement elementToHighlight,
                                                              @NonNls String tag,
                                                              final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code>");
    return createDescriptor(elementToHighlight, message, new AddMissingTagFix(tag), manager, isOnTheFly);
  }

  private static ProblemDescriptor createMissingParamTagDescriptor(PsiElement elementToHighlight,
                                                                   PsiParameter param,
                                                                   final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.param.tag", "<code>@param</code>", "<code>" + param.getName() + "</code>");
    return createDescriptor(elementToHighlight, message, new AddMissingParamTagFix(param.getName()), manager, isOnTheFly);
  }

  private static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myName;

    public AddMissingParamTagFix(String name) {
      super("param", name);
      myName = name;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.param.tag", myName);
    }

    @Override
    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PsiDocComment)) return null;
      final PsiDocComment docComment = (PsiDocComment)parent;
      final PsiDocCommentOwner owner = docComment.getOwner();
      if (!(owner instanceof PsiMethod)) return null;
      PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      PsiParameter myParam = ContainerUtil.find(parameters, new Condition<PsiParameter>() {
        @Override
        public boolean value(PsiParameter psiParameter) {
          return myName.equals(psiParameter.getName());
        }
      });
      if (myParam == null) return null;

      PsiDocTag[] tags = docComment.findTagsByName("param");
      if (tags.length == 0) { //insert as first tag or append to description
        tags = docComment.getTags();
        if (tags.length == 0) return null;
        return tags[0];
      }

      PsiParameter nextParam = PsiTreeUtil.getNextSiblingOfType(myParam, PsiParameter.class);
      while (nextParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(nextParam, tag)) {
            return tag;
          }
        }
        nextParam = PsiTreeUtil.getNextSiblingOfType(nextParam, PsiParameter.class);
      }

      PsiParameter prevParam = PsiTreeUtil.getPrevSiblingOfType(myParam, PsiParameter.class);
      while (prevParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(prevParam, tag)) {
            return PsiTreeUtil.getNextSiblingOfType(tag, PsiDocTag.class);
          }
        }
        prevParam = PsiTreeUtil.getPrevSiblingOfType(prevParam, PsiParameter.class);
      }

      return null;
    }

    private static boolean matches(final PsiParameter param, final PsiDocTag tag) {
      final PsiDocTagValue valueElement = tag.getValueElement();
      return valueElement != null && valueElement.getText().trim().startsWith(param.getName());
    }
  }

  private static String extractTagDescription(PsiDocTag tag) {
    StringBuilder buf = new StringBuilder();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
      else if (child instanceof PsiDocTagValue || child instanceof PsiInlineDocTag) {
        buf.append(child.getText());
      }
    }

    return buf.toString().trim();
  }

  private static String extractThrowsTagDescription(PsiDocTag tag) {
    StringBuilder buf = new StringBuilder();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
    }

    return buf.toString().trim();
  }

  private static void checkForBadCharacters(PsiDocComment docComment,
                                            final List<ProblemDescriptor> problems,
                                            final InspectionManager manager, final boolean onTheFly) {
    docComment.accept(new PsiRecursiveElementVisitor(){
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        final ASTNode node = element.getNode();
        if (node != null) {
          if (node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
            problems.add(manager.createProblemDescriptor(element, "Illegal character", (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
          }
        }
      }
    });
  }

  private void checkForPeriodInDoc(PsiElement context,
                                   PsiDocComment docComment,
                                   List<ProblemDescriptor> problems,
                                   InspectionManager manager, boolean onTheFly) {
    if (IGNORE_JAVADOC_PERIOD) return;
    PsiDocTag[] tags = docComment.getTags();
    int dotIndex = docComment.getText().indexOf('.');
    int tagOffset = 0;
    if (dotIndex >= 0) {      //need to find first valid tag
      for (PsiDocTag tag : tags) {
        final String tagName = tag.getName();
        final JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(tag.getProject()).getTagInfo(tagName);
        if (tagInfo != null && tagInfo.isValidInContext(context) && !tagInfo.isInline()) {
          tagOffset = tag.getTextOffset();
          break;
        }
      }
    }

    if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
      problems.add(manager.createProblemDescriptor(docComment.getFirstChild(),
                                                   InspectionsBundle.message("inspection.javadoc.problem.descriptor1"),
                                                   null,
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly, false));
    }
  }

  @Nullable
  private ArrayList<ProblemDescriptor> getTagValuesProblems(PsiElement context, PsiDocTag[] tags, InspectionManager inspectionManager,
                                                            boolean isOnTheFly) {
    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    for (PsiDocTag tag : tags) {
      final JavadocManager manager = JavadocManager.SERVICE.getInstance(tag.getProject());
      String tagName = tag.getName();
      JavadocTagInfo tagInfo = manager.getTagInfo(tagName);

      if (tagInfo == null || !tagInfo.isValidInContext(context)) {
        if (checkTagInfo(inspectionManager, tagInfo, tag, isOnTheFly, problems)) continue;
      }

      PsiDocTagValue value = tag.getValueElement();
      if (tagInfo != null && !tagInfo.isValidInContext(context)) continue;
      String message = tagInfo == null ? null : tagInfo.checkTagValue(value);

      final PsiReference reference = value != null ? value.getReference() : null;
      if (message == null && reference != null) {
        PsiElement element = reference.resolve();
        if (element == null) {
          final int textOffset = value.getTextOffset();

          if (textOffset == value.getTextRange().getEndOffset()) {
            problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.name.expected"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                   isOnTheFly, true));
          }
        }
      }

      if (message != null) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement == null){
          problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message(
            "inspection.javadoc.method.problem.missing.tag.description", "<code>" + tag.getName() + "</code>"), (LocalQuickFix)null,
                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)); }
        else {
          problems.add(createDescriptor(valueElement, message, inspectionManager, isOnTheFly));
        }
      }
      checkInlineTags(inspectionManager, problems, tag.getDataElements(), manager, isOnTheFly);
    }

    return problems.isEmpty() ? null : problems;
  }

  private boolean checkTagInfo(InspectionManager inspectionManager,
                               JavadocTagInfo tagInfo,
                               PsiDocTag tag,
                               boolean isOnTheFly,
                               List<ProblemDescriptor> problems) {
    final String tagName = tag.getName();
    final StringTokenizer tokenizer = new StringTokenizer(myAdditionalJavadocTags, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (Comparing.strEqual(tagName, tokenizer.nextToken())) return true;
    }

    final PsiElement nameElement = tag.getNameElement();
    if (nameElement != null) {
      if (tagInfo == null) {
        problems.add(
          createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.wrong.tag", "<code>" + tagName + "</code>"),
                           new AddUnknownTagToCustoms(tag.getName()), inspectionManager, isOnTheFly));
      }
      else {
        problems.add(createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.disallowed.tag",
                                                                             "<code>" + tagName + "</code>"),
                                      new AddUnknownTagToCustoms(tag.getName()), inspectionManager, isOnTheFly));
      }
    }
    return false;
  }

  private void checkInlineTags(@NotNull InspectionManager inspectionManager,
                               @NotNull List<ProblemDescriptor> problems,
                               @NotNull PsiElement[] dataElements,
                               @NotNull JavadocManager manager,
                               boolean isOnTheFly) {
    for (PsiElement dataElement : dataElements) {
      if (dataElement instanceof PsiInlineDocTag) {
        final PsiInlineDocTag inlineDocTag = (PsiInlineDocTag)dataElement;
        final PsiElement nameElement = inlineDocTag.getNameElement();
        if (manager.getTagInfo(inlineDocTag.getName()) == null) {
          checkTagInfo(inspectionManager, null, inlineDocTag, isOnTheFly, problems);
        }
        if (!IGNORE_POINT_TO_ITSELF) {
          final PsiDocTagValue value = inlineDocTag.getValueElement();
          if (value != null) {
            final PsiReference reference = value.getReference();
            if (reference != null) {
              final PsiElement ref = reference.resolve();
              if (ref != null){
                if (PsiTreeUtil.getParentOfType(inlineDocTag, PsiDocCommentOwner.class) == PsiTreeUtil.getParentOfType(ref, PsiDocCommentOwner.class, false)) {
                  if (nameElement != null) {
                    problems.add(createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.pointing.to.itself"), inspectionManager,
                                                  isOnTheFly));
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private boolean isTagRequired(PsiElement context, @NonNls String tag) {
    if (context instanceof PsiPackage) {
      return isTagRequired(PACKAGE_OPTIONS, tag);
    }

    if (context instanceof PsiClass) {
      if (PsiTreeUtil.getParentOfType(context, PsiClass.class) != null) {
        return isTagRequired(INNER_CLASS_OPTIONS, tag);
      }

      return isTagRequired(TOP_LEVEL_CLASS_OPTIONS, tag);
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

  private boolean isJavaDocRequired(@NotNull final PsiModifierListOwner element) {
    PsiModifierListOwner psiElement = element;
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    int actualAccess = getAccessNumber(refUtil.getAccessModifier(psiElement));
    if (psiElement instanceof PsiPackage) {
      return 1 <= getAccessNumber(PACKAGE_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)psiElement;
      if (PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null) {
        return actualAccess <= getAccessNumber(INNER_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
      }

      return actualAccess <= getAccessNumber(TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiMethod) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiField) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    return false;
  }

  private void checkDuplicateTags(final PsiDocTag[] tags,
                                  List<ProblemDescriptor> problems,
                                  final InspectionManager manager, boolean isOnTheFly) {
    Set<String> documentedParamNames = null;
    Set<String> documentedExceptions = null;
    Set<String> uniqueTags = null;
    for(PsiDocTag tag: tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiDocParamRef paramRef = (PsiDocParamRef)value;
          final PsiReference reference = paramRef.getReference();
          if (reference != null) {
            final String paramName = reference.getCanonicalText();
            if (documentedParamNames == null) {
              documentedParamNames = new HashSet<String>();
            }
            if (documentedParamNames.contains(paramName)) {
              problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.param", paramName), manager,
                                            isOnTheFly));
            }
            documentedParamNames.add(paramName);
          }
        }
      }
      else if (!myIgnoreDuplicatedThrows && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          final PsiElement firstChild = value.getFirstChild();
          if (firstChild != null && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)firstChild.getFirstChild();
            PsiElement element = refElement.resolve();
            if (element instanceof PsiClass) {
              String fqName = ((PsiClass)element).getQualifiedName();
              if (documentedExceptions == null) {
                documentedExceptions = new HashSet<String>();
              }
              if (documentedExceptions.contains(fqName)) {
                problems.add(createDescriptor(tag.getNameElement(),
                                              InspectionsBundle.message("inspection.javadoc.problem.duplicate.throws", fqName),
                                              manager, isOnTheFly));
              }
              documentedExceptions.add(fqName);
            }
          }
        }
      }
      else if (ourUniqueTags.contains(tag.getName())) {
        if (uniqueTags == null) {
          uniqueTags = new HashSet<String>();
        }
        if (uniqueTags.contains(tag.getName())) {
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()), manager,
                                        isOnTheFly));
        }
        uniqueTags.add(tag.getName());
      }
    }
  }

  private static int getAccessNumber(@NonNls String accessModifier) {
    if (accessModifier.startsWith("none")) return 0;
    if (accessModifier.startsWith("public")) return 1;
    if (accessModifier.startsWith("protected")) return 2;
    if (accessModifier.startsWith("package")) return 3;
    if (accessModifier.startsWith("private")) return 4;

    return 5;
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

  public void setIgnoreEmptyDescriptions(boolean ignoreEmptyDescriptions) {
    myIgnoreEmptyDescriptions = ignoreEmptyDescriptions;
  }

  private class AddUnknownTagToCustoms implements LocalQuickFix {
    private final String myTag;

    public AddUnknownTagToCustoms(String tag) {
      myTag = tag;
    }

    @Override
    @NotNull
    public String getName() {
      return QuickFixBundle.message("add.docTag.to.custom.tags", myTag);
    }

    @Override
    @NotNull
    public String getFamilyName() {
     return QuickFixBundle.message("fix.javadoc.family");
   }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (myTag == null) return;
      if (!myAdditionalJavadocTags.isEmpty()) {
        myAdditionalJavadocTags += "," + myTag;
      }
      else {
        myAdditionalJavadocTags = myTag;
      }
      final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      //correct save settings
      InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
      //TODO lesya

      /*

      try {
        inspectionProfile.save();
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
      }

      */
    }
  }
}

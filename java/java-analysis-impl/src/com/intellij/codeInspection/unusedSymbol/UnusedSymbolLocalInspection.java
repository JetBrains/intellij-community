// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.LocalRefUseInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceWithUnnamedPatternFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * Local counterpart of {@link UnusedDeclarationInspectionBase}
 */
public final class UnusedSymbolLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls public static final String UNUSED_PARAMETERS_SHORT_NAME = "UnusedParameters";
  @NonNls public static final String UNUSED_ID = "unused";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  private boolean INNER_CLASS = true;
  public boolean PARAMETER = true;
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;

  private String myClassVisibility = PsiModifier.PUBLIC;
  private String myInnerClassVisibility = PsiModifier.PUBLIC;
  private String myFieldVisibility = PsiModifier.PUBLIC;
  private String myMethodVisibility = PsiModifier.PUBLIC;
  private String myParameterVisibility = PsiModifier.PUBLIC;
  private boolean myIgnoreAccessors = false;
  private boolean myCheckParameterExcludingHierarchy = false;

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    LocalRefUseInfo info = LocalRefUseInfo.forFile(file);
    Project project = holder.getProject();
    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    UnusedDeclarationInspectionBase deadCodeInspection = ObjectUtils.tryCast(
      profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, file), UnusedDeclarationInspectionBase.class);
    GlobalUsageHelper helper = info.getGlobalUsageHelper(file, deadCodeInspection);
    return new JavaElementVisitor() {
      private final QuickFixFactory fixFactory = QuickFixFactory.getInstance();
      private final Map<PsiMethod, Boolean> isOverriddenOrOverrides = ConcurrentFactoryMap.createMap(
        method -> {
          boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
          return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
        }
      );

      private boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
        return isOverriddenOrOverrides.get(method);
      }

      private static boolean compareVisibilities(PsiModifierListOwner listOwner, String visibility) {
        if (visibility != null) {
          while (listOwner != null) {
            if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(listOwner.getModifierList()), visibility) >= 0) {
              return true;
            }
            listOwner = PsiTreeUtil.getParentOfType(listOwner, PsiModifierListOwner.class, true);
          }
        }
        return false;
      }

      private void registerProblem(@NotNull PsiElement element,
                                   @NotNull @InspectionMessage String message,
                                   @NotNull List<? extends IntentionAction> fixes) {
        // Synthetic elements in JSP like JspHolderMethod may arrive here
        if (element instanceof SyntheticElement) return;
        if (element instanceof PsiNameIdentifierOwner owner) {
          PsiElement identifier = owner.getNameIdentifier();
          if (identifier != null) {
            element = identifier;
          }
        }
        for (UnusedDeclarationFixProvider provider : UnusedDeclarationFixProvider.EP_NAME.getExtensionList()) {
          IntentionAction[] additionalFixes = provider.getQuickFixes(element);
          fixes = ContainerUtil.append(fixes, additionalFixes);
        }
        holder.registerProblem(element, message, ContainerUtil.map2Array(fixes, LocalQuickFix.EMPTY_ARRAY,
                                                                         UnusedSymbolLocalInspection::toLocalQuickFix));
      }

      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        if (!LOCAL_VARIABLE) return;
        if (variable.isUnnamed() || PsiUtil.isIgnoredName(variable.getName())) return;
        if (UnusedSymbolUtil.isImplicitUsage(project, variable)) return;

        if (!info.isReferenced(variable)) {
          IntentionAction fix = variable instanceof PsiResourceVariable
                                ? fixFactory.createRenameToIgnoredFix(variable, false)
                                : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
          registerProblem(variable, JavaErrorBundle.message("local.variable.is.never.used", variable.getName()), List.of(fix));
        }

        else if (!info.isReferencedForRead(variable) && !UnusedSymbolUtil.isImplicitRead(project, variable)) {
          registerProblem(variable, JavaErrorBundle.message("local.variable.is.not.used.for.reading", variable.getName()),
                          List.of(QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable)));
        }

        else if (!variable.hasInitializer() &&
                 !info.isReferencedForWrite(variable) &&
                 !UnusedSymbolUtil.isImplicitWrite(project, variable)) {
          registerProblem(variable, JavaErrorBundle.message("local.variable.is.not.assigned", variable.getName()),
                          List.of(fixFactory.createAddVariableInitializerFix(variable)));
        }
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        if (!compareVisibilities(field, getFieldVisibility())) return;
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) return;
        if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
          if (!info.isReferenced(field) && !UnusedSymbolUtil.isImplicitUsage(project, field)) {
            List<IntentionAction> fixes = new ArrayList<>(suggestionsToMakeFieldUsed(field));
            if (!field.hasInitializer() && !field.hasModifierProperty(PsiModifier.FINAL)) {
              fixes.add(fixFactory.createCreateConstructorParameterFromFieldFix(field));
            }
            registerProblem(field, JavaErrorBundle.message("private.field.is.not.used", field.getName()), fixes);
            return;
          }

          boolean readReferenced = info.isReferencedForRead(field);
          if (!readReferenced && !UnusedSymbolUtil.isImplicitRead(project, field)) {
            registerProblem(field, getNotUsedForReadingMessage(field), suggestionsToMakeFieldUsed(field));
            return;
          }

          if (field.hasInitializer()) {
            return;
          }
          boolean writeReferenced = info.isReferencedForWrite(field);
          if (!writeReferenced && !UnusedSymbolUtil.isImplicitWrite(project, field)) {
            List<IntentionAction> fixes = new ArrayList<>();
            fixes.add(fixFactory.createCreateGetterOrSetterFix(false, true, field));
            if (!field.hasModifierProperty(PsiModifier.FINAL)) {
              fixes.add(fixFactory.createCreateConstructorParameterFromFieldFix(field));
            }
            SpecialAnnotationsUtilBase.processUnknownAnnotations(field, annoName ->
              fixes.add(fixFactory.createAddToImplicitlyWrittenFieldsFix(project, annoName)));
            registerProblem(field, JavaErrorBundle.message("private.field.is.not.assigned", field.getName()), fixes);
          }
        }
        else if (!UnusedSymbolUtil.isFieldUsed(project, file, field, helper)) {
          if (UnusedSymbolUtil.isImplicitWrite(project, field)) {
            registerProblem(field, getNotUsedForReadingMessage(field), List.of(fixFactory.createSafeDeleteFix(field)));
          }
          else if (!UnusedSymbolUtil.isImplicitUsage(project, field)) {
            formatUnusedSymbolHighlightInfo("field.is.not.used", field);
          }
        }
      }

      private void formatUnusedSymbolHighlightInfo(@NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern,
                                                   @NotNull PsiMember member) {
        List<IntentionAction> fixes = new ArrayList<>();
        fixes.add(QuickFixFactory.getInstance().createSafeDeleteFix(member));
        SpecialAnnotationsUtilBase.processUnknownAnnotations(member, annoName ->
          fixes.add(QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName)));
        registerProblem(member, JavaErrorBundle.message(pattern, member.getName()), fixes);
      }

      private static boolean isUsedMainOrPremainMethod(@NotNull PsiMethod method) {
        if (!PsiClassImplUtil.isMainOrPremainMethod(method)) {
          return false;
        }
        //premain
        if (!"main".equals(method.getName())) {
          return true;
        }
        return !PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, method);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (isIgnoreAccessors() && PropertyUtilBase.isSimplePropertyAccessor(method)) return;
        if (!compareVisibilities(method, getMethodVisibility())) return;
        if (UnusedSymbolUtil.isMethodUsed(project, file, method, helper)) return;
        @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key;
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
          key = method.isConstructor() ? "private.constructor.is.not.used" : "private.method.is.not.used";
        }
        else {
          key = method.isConstructor() ? "constructor.is.not.used" : "method.is.not.used";
        }
        int options = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES;
        String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY, options);
        QuickFixFactory factory = QuickFixFactory.getInstance();
        List<IntentionAction> fixes = new ArrayList<>();
        fixes.add(factory.createSafeDeleteFix(method));
        if (ApplicationManager.getApplication().isHeadlessEnvironment() && method.hasModifierProperty(PsiModifier.PRIVATE)) {
          fixes.add(factory.createDeletePrivateMethodFix(method).asIntention());
        }
        SpecialAnnotationsUtilBase.processUnknownAnnotations(method, annoName ->
          fixes.add(factory.createAddToDependencyInjectionAnnotationsFix(project, annoName)));
        registerProblem(method, JavaErrorBundle.message(key, symbolName), fixes);
      }

      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        PsiElement declarationScope = parameter.getDeclarationScope();
        boolean needToProcessParameter;
        if (declarationScope instanceof PsiMethod method) {
          needToProcessParameter = compareVisibilities(method, getParameterVisibility());
        }
        else if (declarationScope instanceof PsiLambdaExpression) {
          needToProcessParameter = compareVisibilities(
            PsiTreeUtil.getParentOfType(declarationScope, PsiModifierListOwner.class), getParameterVisibility());
        }
        else {
          needToProcessParameter = LOCAL_VARIABLE;
        }
        if (!needToProcessParameter) return;
        if (parameter.isUnnamed() || PsiUtil.isIgnoredName(parameter.getName())) return;
        QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
        if (declarationScope instanceof PsiMethod method) {
          if (PsiUtilCore.hasErrorElementChild(method)) return;
          if ((method.isConstructor() ||
               method.hasModifierProperty(PsiModifier.PRIVATE) ||
               method.hasModifierProperty(PsiModifier.STATIC) ||
               !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
               (!isOverriddenOrOverrides(method) || shouldCheckParameterExcludingHierarchy())) &&
              !method.hasModifierProperty(PsiModifier.NATIVE) &&
              !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
              !isUsedMainOrPremainMethod(method)) {
            if (UnusedSymbolUtil.isInjected(project, method)) return;
            String message = checkUnusedParameter(parameter);
            if (message != null) {
              registerProblem(
                parameter, message,
                ContainerUtil.append(
                  getFixesForUnusedParameter(method, parameter),
                  quickFixFactory.createRenameToIgnoredFix(parameter, true),
                  PriorityIntentionActionWrapper.highPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(
                    parameter, shouldCheckParameterExcludingHierarchy() && isOverriddenOrOverrides(method)))));
            }
          }
        }
        else if (declarationScope instanceof PsiForeachStatement) {
          String message = checkUnusedParameter(parameter);
          if (message != null) {
            registerProblem(parameter, message, List.of(quickFixFactory.createRenameToIgnoredFix(parameter, false)));
          }
        }
        else if (parameter instanceof PsiPatternVariable variable) {
          String message = checkUnusedParameter(parameter);
          if (message != null) {
            PsiPattern pattern = variable.getPattern();
            IntentionAction action = null;
            if (PsiUtil.isAvailable(JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES, parameter)) {
              if (pattern instanceof PsiTypeTestPattern ttPattern && pattern.getParent() instanceof PsiDeconstructionList) {
                PsiRecordComponent component = JavaPsiPatternUtil.getRecordComponentForPattern(pattern);
                PsiTypeElement checkType = ttPattern.getCheckType();
                if (component != null && checkType != null && checkType.getType().isAssignableFrom(component.getType())) {
                  action = new ReplaceWithUnnamedPatternFix(pattern).asIntention();
                }
              }
            }
            if (action == null && declarationScope.getParent() instanceof PsiSwitchBlock) {
              action = variable.getParent() instanceof PsiDeconstructionPattern
                       ? quickFixFactory.createDeleteFix(parameter)
                       : quickFixFactory.createRenameToIgnoredFix(parameter, false);
            }
            else if (!(pattern instanceof PsiTypeTestPattern && pattern.getParent() instanceof PsiDeconstructionList)) {
              action = quickFixFactory.createDeleteFix(parameter);
            }
            registerProblem(parameter, message, ContainerUtil.createMaybeSingletonList(action));
          }
        }
        else if ((shouldCheckParameterExcludingHierarchy() ||
                  PsiUtil.isAvailable(JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES, declarationScope))
                 && declarationScope instanceof PsiLambdaExpression) {
          String message = checkUnusedParameter(parameter);
          if (message != null) {
            registerProblem(
              parameter, message,
              List.of(quickFixFactory.createRenameToIgnoredFix(parameter, true),
                      PriorityIntentionActionWrapper.lowPriority(
                        quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, true))));
          }
        }
      }

      private @Nullable @InspectionMessage String checkUnusedParameter(@NotNull PsiParameter parameter) {
        if (!info.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(project, parameter)) {
          return JavaErrorBundle.message(parameter instanceof PsiPatternVariable ?
                                         "pattern.variable.is.not.used" : "parameter.is.not.used", parameter.getName());
        }
        return null;
      }

      private static @NotNull List<IntentionAction> getFixesForUnusedParameter(@NotNull PsiMethod declarationMethod, @NotNull PsiParameter parameter) {
        IntentionAction assignFix = QuickFixFactory.getInstance().createAssignFieldFromParameterFix(parameter);
        IntentionAction createFieldFix = QuickFixFactory.getInstance().createCreateFieldFromParameterFix(parameter);
        if (!declarationMethod.isConstructor()) {
          assignFix = PriorityIntentionActionWrapper.lowPriority(assignFix);
          createFieldFix = PriorityIntentionActionWrapper.lowPriority(createFieldFix);
        }
        return List.of(assignFix, createFieldFix);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass instanceof PsiImplicitClass) return;
        String acceptedVisibility = aClass.getContainingClass() == null ? getClassVisibility() : getInnerClassVisibility();
        if (!compareVisibilities(aClass, acceptedVisibility)) return;
        if (UnusedSymbolUtil.isClassUsed(project, file, aClass, helper)) return;

        @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern;
        if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          pattern = aClass.isInterface() ? "private.inner.interface.is.not.used" : "private.inner.class.is.not.used";
        }
        else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
          pattern = "local.class.is.not.used";
        }
        else if (aClass instanceof PsiTypeParameter) {
          pattern = "type.parameter.is.not.used";
        }
        else if (aClass.isAnnotationType()) {
          pattern = "annotation.interface.is.not.used";
        }
        else if (aClass.isInterface()) {
          pattern = "interface.is.not.used";
        }
        else if (aClass.isEnum()) {
          pattern = "enum.is.not.used";
        }
        else if (aClass.isRecord()) {
          pattern = "record.is.not.used";
        }
        else {
          pattern = "class.is.not.used";
        }
        formatUnusedSymbolHighlightInfo(pattern, aClass);
      }

      @NotNull
      private static @NlsContexts.DetailedDescription String getNotUsedForReadingMessage(@NotNull PsiField field) {
        String visibility = VisibilityUtil.getVisibilityStringToDisplay(field);
        String message = JavaErrorBundle.message("field.is.not.used.for.reading", visibility, field.getName());
        return StringUtil.capitalize(message);
      }

      private List<IntentionAction> suggestionsToMakeFieldUsed(@NotNull PsiField field) {
        List<IntentionAction> quickFixes = new ArrayList<>();
        SpecialAnnotationsUtilBase.processUnknownAnnotations(field, annoName ->
          quickFixes.add(EntryPointsManagerBase.createAddEntryPointAnnotation(annoName).asIntention()));
        quickFixes.add(QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field));
        quickFixes.add(fixFactory.createCreateGetterOrSetterFix(true, false, field));
        quickFixes.add(fixFactory.createCreateGetterOrSetterFix(false, true, field));
        quickFixes.add(fixFactory.createCreateGetterOrSetterFix(true, true, field));
        return quickFixes;
      }
    };
  }

  private static @NotNull LocalQuickFix toLocalQuickFix(IntentionAction fix) {
    ModCommandAction action = fix.asModCommandAction();
    return action == null ? new LocalQuickFixBackedByIntentionAction(fix) :
           LocalQuickFix.from(action);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("CLASS", JavaBundle.message("inspection.unused.symbol.check.classes"),
               modifierSelector("myClassVisibility")),
      checkbox("INNER_CLASS", JavaBundle.message("inspection.unused.symbol.check.inner.classes"),
               modifierSelector("myInnerClassVisibility")),
      checkbox("FIELD", JavaBundle.message("inspection.unused.symbol.check.fields"),
               modifierSelector("myFieldVisibility")),
      checkbox("METHOD", JavaBundle.message("inspection.unused.symbol.check.methods"),
               modifierSelector("myMethodVisibility"),
               checkbox("myIgnoreAccessors", JavaBundle.message("inspection.unused.symbol.check.accessors"))),
      checkbox("PARAMETER", JavaBundle.message("inspection.unused.symbol.check.parameters"),
               modifierSelector("myParameterVisibility"),
               checkbox("myCheckParameterExcludingHierarchy",
                        JavaBundle.message("inspection.unused.symbol.check.parameters.excluding.hierarchy"))),
      checkbox("LOCAL_VARIABLE", JavaBundle.message("inspection.unused.symbol.check.localvars"))
    );
  }

  private static OptDropdown modifierSelector(@Language("jvm-field-name") @NotNull String bindId) {
    return dropdown(bindId, "", List.of(AccessModifier.values()),
                    AccessModifier::toPsiModifier, AccessModifier::toString);
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getClassVisibility() {
    if (!CLASS) return null;
    return myClassVisibility;
  }
  @PsiModifier.ModifierConstant
  @Nullable
  public String getFieldVisibility() {
    if (!FIELD) return null;
    return myFieldVisibility;
  }
  @PsiModifier.ModifierConstant
  @Nullable
  public String getMethodVisibility() {
    if (!METHOD) return null;
    return myMethodVisibility;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getParameterVisibility() {
    if (!PARAMETER) return null;
    return myParameterVisibility;
  }

  private boolean shouldCheckParameterExcludingHierarchy() {
    return myCheckParameterExcludingHierarchy;
  }

  @PsiModifier.ModifierConstant
  @Nullable
  public String getInnerClassVisibility() {
    if (!INNER_CLASS) return null;
    return myInnerClassVisibility;
  }

  @TestOnly
  public void setClassVisibility(String classVisibility) {
    this.myClassVisibility = classVisibility;
  }

  @TestOnly
  public void setParameterVisibility(String parameterVisibility) {
    REPORT_PARAMETER_FOR_PUBLIC_METHODS = PsiModifier.PUBLIC.equals(parameterVisibility);
    this.myParameterVisibility = parameterVisibility;
  }

  @TestOnly
  public void setCheckParameterExcludingHierarchy(boolean checkParameterExcludingHierarchy) {
    this.myCheckParameterExcludingHierarchy = checkParameterExcludingHierarchy;
  }

  public boolean isIgnoreAccessors() {
    return myIgnoreAccessors;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @NonNls
  public String getID() {
    return UNUSED_ID;
  }

  @Override
  public String getAlternativeID() {
    return UnusedDeclarationInspectionBase.ALTERNATIVE_ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    writeVisibility(node, myClassVisibility, "klass");
    writeVisibility(node, myInnerClassVisibility, "inner_class");
    writeVisibility(node, myFieldVisibility, "field");
    writeVisibility(node, myMethodVisibility, "method");
    writeVisibility(node, "parameter", myParameterVisibility, getParameterDefaultVisibility());
    if (myIgnoreAccessors) {
      node.setAttribute("ignoreAccessors", Boolean.toString(true));
    }
    if (!INNER_CLASS) {
      node.setAttribute("INNER_CLASS", Boolean.toString(false));
    }
    node.setAttribute("checkParameterExcludingHierarchy", Boolean.toString(myCheckParameterExcludingHierarchy));
    super.writeSettings(node);
  }

  private static void writeVisibility(Element node, String visibility, String type) {
    writeVisibility(node, type, visibility, PsiModifier.PUBLIC);
  }

  private static void writeVisibility(Element node,
                                      String type,
                                      String visibility,
                                      String defaultVisibility) {
    if (!defaultVisibility.equals(visibility)) {
      node.setAttribute(type, visibility);
    }
  }

  private String getParameterDefaultVisibility() {
    return REPORT_PARAMETER_FOR_PUBLIC_METHODS ? PsiModifier.PUBLIC : PsiModifier.PRIVATE;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    myClassVisibility = readVisibility(node, "klass");
    myInnerClassVisibility = readVisibility(node, "inner_class");
    myFieldVisibility = readVisibility(node, "field");
    myMethodVisibility = readVisibility(node, "method");
    myParameterVisibility = readVisibility(node, "parameter", getParameterDefaultVisibility());
    final String ignoreAccessors = node.getAttributeValue("ignoreAccessors");
    myIgnoreAccessors = Boolean.parseBoolean(ignoreAccessors);
    final String innerClassEnabled = node.getAttributeValue("INNER_CLASS");
    INNER_CLASS = innerClassEnabled == null || Boolean.parseBoolean(innerClassEnabled);
    final String checkParameterExcludingHierarchy = node.getAttributeValue("checkParameterExcludingHierarchy");
    myCheckParameterExcludingHierarchy = Boolean.parseBoolean(checkParameterExcludingHierarchy);
  }

  private static String readVisibility(@NotNull Element node, final String type) {
    return readVisibility(node, type, PsiModifier.PUBLIC);
  }

  private static String readVisibility(@NotNull Element node,
                                       final String type,
                                       final String defaultVisibility) {
    final String visibility = node.getAttributeValue(type);
    if (visibility == null) {
      return defaultVisibility;
    }
    return visibility;
  }
}

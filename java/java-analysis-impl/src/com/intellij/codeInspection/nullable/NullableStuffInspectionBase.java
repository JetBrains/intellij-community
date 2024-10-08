// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationOnStaticMemberQualifyingTypeFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.AddTypeAnnotationFix;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.impl.search.JavaOverridingMethodsSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.codeInsight.AnnotationUtil.*;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.util.ObjectUtils.tryCast;

public class NullableStuffInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  /**
   * @deprecated field remains to minimize changes to users inspection profiles.
   */
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  /**
   * @deprecated field remains to minimize changes to users inspection profiles.
   */
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @SuppressWarnings("WeakerAccess") public boolean IGNORE_EXTERNAL_SUPER_NOTNULL;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED;
  /**
   * @deprecated field remains to minimize changes to users inspection profiles.
   */
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  /**
   * @deprecated field remains for test
   */
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
  /**
   * @deprecated field remains to minimize changes to users inspection profiles.
   */
  @Deprecated @SuppressWarnings("WeakerAccess") public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;

  private static final Logger LOG = Logger.getInstance(NullableStuffInspectionBase.class);

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    for (Element child : new ArrayList<>(node.getChildren())) {
      String name = child.getAttributeValue("name");
      String value = child.getAttributeValue("value");
      if ("IGNORE_EXTERNAL_SUPER_NOTNULL".equals(name) && "false".equals(value) ||
          "REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED".equals(name) && "false".equals(value) ||
          "REQUIRE_NOTNULL_FIELDS_INITIALIZED".equals(name) && "true".equals(value) ||
          "REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER".equals(name) && "true".equals(value)) {
        node.removeContent(child);
      }
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, file) || nullabilityAnnotationsNotAvailable(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      private final NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
      private final List<String> nullables = manager.getNullables();
      private final List<String> notNulls = manager.getNotNulls();

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkNullableStuffForMethod(method, holder);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass.isRecord()) {
          PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
          if (constructor instanceof SyntheticElement) {
            checkParameters(constructor, holder, List.of(), manager);
          }
        }
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        checkMethodReference(expression, holder);

        JavaResolveResult result = expression.advancedResolve(false);
        PsiElement target = result.getElement();
        if (target instanceof PsiMethod) {
          checkCollectionNullityOnAssignment(expression,
                                             LambdaUtil.getFunctionalInterfaceReturnType(expression),
                                             result.getSubstitutor().substitute(((PsiMethod)target).getReturnType()));
        }
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        final PsiType type = field.getType();
        final Annotated annotated = check(field, holder, type);
        if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
          return;
        }
        Project project = holder.getProject();
        if (annotated.isDeclaredNotNull ^ annotated.isDeclaredNullable) {
          final String anno = annotated.isDeclaredNotNull ? manager.getDefaultNotNull() : manager.getDefaultNullable();
          final List<String> annoToRemove = annotated.isDeclaredNotNull ? nullables : notNulls;

          if (!checkNonStandardAnnotations(field, annotated, anno, holder)) return;

          checkAccessors(field, annotated, project, manager, anno, annoToRemove, holder);

          checkConstructorParameters(field, annotated, manager, anno, annoToRemove, holder);
        }
      }

      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        check(parameter, holder, parameter.getType());
      }

      @Override
      public void visitAnnotation(@NotNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) return;
        Optional<Nullability> nullabilityOptional = manager.getAnnotationNullability(qualifiedName);
        if (nullabilityOptional.isEmpty()) return;
        Nullability nullability = nullabilityOptional.get();
        PsiType type = AnnotationUtil.getRelatedType(annotation);
        PsiAnnotationOwner owner = annotation.getOwner();
        PsiModifierListOwner listOwner = owner instanceof PsiModifierList modifierList
                                         ? tryCast(modifierList.getParent(), PsiModifierListOwner.class) : null;
        PsiType targetType = listOwner instanceof PsiMethod method ? method.getReturnType() :
                             listOwner instanceof PsiVariable variable ? variable.getType() : null;
        if (type instanceof PsiPrimitiveType) {
          LocalQuickFix additionalFix = null;
          if (targetType instanceof PsiArrayType && targetType.getAnnotations().length == 0) {
            additionalFix = new MoveAnnotationToArrayFix();
          }
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.primitive.type.annotation", LocalQuickFix.notNullElements(additionalFix));
        }
        if (type instanceof PsiClassType classType) {
          PsiElement context = classType.getPsiContext();
          // outer type/package
          if (context instanceof PsiJavaCodeReferenceElement outerCtx) {
            PsiElement parent = context.getParent();
            if (parent instanceof PsiJavaCodeReferenceElement) {
              if (outerCtx.resolve() instanceof PsiPackage) {
                ModCommandAction action = new MoveAnnotationOnStaticMemberQualifyingTypeFix(annotation);
                reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.applied.to.package",
                                        LocalQuickFix.from(action));
              }
              else {
                ModCommandAction action = new MoveAnnotationOnStaticMemberQualifyingTypeFix(annotation);
                reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.outer.type",
                                        LocalQuickFix.from(action));
              }
            }
            if (parent instanceof PsiReferenceList) {
              PsiElement firstChild = parent.getFirstChild();
              if ((PsiUtil.isJavaToken(firstChild, JavaTokenType.EXTENDS_KEYWORD) ||
                   PsiUtil.isJavaToken(firstChild, JavaTokenType.IMPLEMENTS_KEYWORD)) &&
                  !(parent.getParent() instanceof PsiTypeParameter)) {
                reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.at.reference.list");
              }
            }
          }
        }
        if (type instanceof PsiArrayType && annotation.getParent() instanceof PsiTypeElement parent &&
            parent.getType().equals(type) && !manager.canAnnotateLocals(qualifiedName)) {
          checkIllegalLocalAnnotation(annotation, parent.getParent());
        }
        if (listOwner instanceof PsiMethod method && method.isConstructor()) {
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.at.constructor");
        }
        if (listOwner instanceof PsiEnumConstant) {
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.at.enum.constant");
        }
        if (!manager.canAnnotateLocals(qualifiedName) && !(targetType instanceof PsiArrayType)) {
          checkIllegalLocalAnnotation(annotation, listOwner);
        }
        if (type instanceof PsiWildcardType && manager.isTypeUseAnnotationLocationRestricted(qualifiedName)) {
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.at.wildcard");
        }
        if (owner instanceof PsiTypeParameter && manager.isTypeUseAnnotationLocationRestricted(qualifiedName)) {
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.at.type.parameter");
        }
        if (listOwner instanceof PsiReceiverParameter && nullability != Nullability.NOT_NULL) {
          reportIncorrectLocation(holder, annotation, listOwner, "inspection.nullable.problems.receiver.annotation");
        }
        checkOppositeAnnotationConflict(annotation, nullability);
        if (AnnotationUtil.NOT_NULL.equals(qualifiedName)) {
          PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("exception");
          if (value instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
            PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression.getOperand().getType());
            if (psiClass != null && !hasStringConstructor(psiClass)) {
              reportProblem(holder, value, "custom.exception.class.should.have.a.constructor");
            }
          }
        }
      }

      private void checkIllegalLocalAnnotation(@NotNull PsiAnnotation annotation, @Nullable PsiElement owner) {
        if (owner instanceof PsiLocalVariable ||
            owner instanceof PsiParameter parameter &&
            parameter.getDeclarationScope() instanceof PsiCatchSection) {
          reportIncorrectLocation(holder, annotation, (PsiVariable)owner, "inspection.nullable.problems.at.local.variable");
        }
      }

      private void checkOppositeAnnotationConflict(PsiAnnotation annotation, Nullability nullability) {
        PsiAnnotationOwner owner = annotation.getOwner();
        if (owner == null) return;
        PsiModifierListOwner listOwner = owner instanceof PsiModifierList modifierList
                                         ? tryCast(modifierList.getParent(), PsiModifierListOwner.class)
                                         : null;
        Condition<PsiAnnotation> filter = anno ->
          anno != annotation && manager.getAnnotationNullability(anno.getQualifiedName()).filter(n -> n != nullability).isPresent();
        PsiAnnotation oppositeAnno = ContainerUtil.find(owner.getAnnotations(), filter);
        if (oppositeAnno == null && listOwner != null) {
          oppositeAnno = manager.findExplicitNullabilityAnnotation(
            listOwner, ContainerUtil.filter(Nullability.values(), n -> n != nullability));
        }
        if (oppositeAnno != null &&
            Objects.equals(AnnotationUtil.getRelatedType(annotation), AnnotationUtil.getRelatedType(oppositeAnno))) {
          reportProblem(holder, annotation, new RemoveAnnotationQuickFix(annotation, listOwner),
                        "inspection.nullable.problems.Nullable.NotNull.conflict",
                        getPresentableAnnoName(annotation), getPresentableAnnoName(oppositeAnno));
        }
      }

      private static boolean hasStringConstructor(PsiClass aClass) {
        for (PsiMethod method : aClass.getConstructors()) {
          PsiParameterList list = method.getParameterList();
          if (list.getParametersCount() == 1 &&
              Objects.requireNonNull(list.getParameter(0)).getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        checkNullableNotNullInstantiationConflict(reference);

        PsiElement list = reference.getParent();
        PsiElement psiClass = list instanceof PsiReferenceList ? list.getParent() : null;
        PsiElement intf = reference.resolve();
        if (psiClass instanceof PsiClass && list == ((PsiClass)psiClass).getImplementsList() &&
            intf instanceof PsiClass && ((PsiClass)intf).isInterface()) {
          String error = checkIndirectInheritance(psiClass, (PsiClass)intf);
          if (error != null) {
            holder.registerProblem(reference, error);
          }
        }
      }

      private void checkNullableNotNullInstantiationConflict(PsiJavaCodeReferenceElement reference) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass) {
          PsiTypeParameter[] typeParameters = ((PsiClass)element).getTypeParameters();
          PsiTypeElement[] typeArguments = getReferenceTypeArguments(reference);
          if (typeParameters.length > 0 && typeParameters.length == typeArguments.length && !(typeArguments[0].getType() instanceof PsiDiamondType)) {
            for (int i = 0; i < typeParameters.length; i++) {
              PsiTypeElement typeArgument = typeArguments[i];
              Project project = element.getProject();
              PsiType type = typeArgument.getType();
              if (DfaPsiUtil.getTypeNullability(JavaPsiFacade.getElementFactory(project).createType(typeParameters[i])) ==
                  Nullability.NOT_NULL) {
                Nullability typeNullability = DfaPsiUtil.getTypeNullability(type);
                if (typeNullability != Nullability.NOT_NULL &&
                    !(typeNullability == Nullability.UNKNOWN && type instanceof PsiWildcardType && !((PsiWildcardType)type).isExtends())) {
                  String annotationToAdd = manager.getDefaultNotNull();
                  PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationToAdd, element.getResolveScope());
                  AddTypeAnnotationFix fix = null;
                  if (annotationClass != null &&
                      AnnotationTargetUtil.findAnnotationTarget(annotationClass, PsiAnnotation.TargetType.TYPE_USE) != null) {
                    fix = new AddTypeAnnotationFix(annotationToAdd, manager.getNullables());
                  }
                  reportProblem(holder, typeArgument, fix, "non.null.type.argument.is.expected");
                }
              }
            }
          }
        }
      }

      private static PsiTypeElement[] getReferenceTypeArguments(PsiJavaCodeReferenceElement reference) {
        PsiReferenceParameterList typeArgList = reference.getParameterList();
        return typeArgList == null ? PsiTypeElement.EMPTY_ARRAY : typeArgList.getTypeParameterElements();
      }

      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        checkCollectionNullityOnAssignment(expression.getOperationSign(), expression.getLExpression().getType(), expression.getRExpression());
      }

      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null) {
          checkCollectionNullityOnAssignment(identifier, variable.getType(), variable.getInitializer());
        }
      }

      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) return;

        checkCollectionNullityOnAssignment(statement.getReturnValue(), PsiTypesUtil.getMethodReturnType(statement), returnValue);
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkCollectionNullityOnAssignment(body, LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body);
        }
      }

      @Override
      public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
        PsiExpressionList argList = callExpression.getArgumentList();
        JavaResolveResult result = callExpression.resolveMethodGenerics();
        PsiMethod method = (PsiMethod)result.getElement();
        if (method == null || argList == null) return;

        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiExpression[] arguments = argList.getExpressions();
        for (int i = 0; i < arguments.length; i++) {
          PsiExpression argument = arguments[i];
          if (i < parameters.length &&
              (i < parameters.length - 1 || !MethodCallInstruction.isVarArgCall(method, substitutor, arguments, parameters))) {
            checkCollectionNullityOnAssignment(argument, substitutor.substitute(parameters[i].getType()), argument);
          }
        }
      }

      private void checkCollectionNullityOnAssignment(@NotNull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiExpression assignedExpression) {
        if (assignedExpression == null) return;

        checkCollectionNullityOnAssignment(errorElement, expectedType, assignedExpression.getType());
      }

      private void checkCollectionNullityOnAssignment(@NotNull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiType assignedType) {
        if (isNullableNotNullCollectionConflict(expectedType, assignedType, file, new HashSet<>())) {
          reportProblem(holder, errorElement, "assigning.a.collection.of.nullable.elements");
        }
      }
    };
  }

  private void reportProblem(@NotNull ProblemsHolder holder, PsiElement anchor, String messageKey, Object... args) {
    reportProblem(holder, anchor, LocalQuickFix.EMPTY_ARRAY, messageKey, args);
  }

  private void reportProblem(@NotNull ProblemsHolder holder, @NotNull PsiElement anchor, @Nullable LocalQuickFix fix,
                             @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String messageKey, Object... args) {
    reportProblem(holder, anchor, fix == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[] {fix}, messageKey, args);
  }

  protected void reportProblem(@NotNull ProblemsHolder holder, @NotNull PsiElement anchor, @NotNull LocalQuickFix @NotNull [] fixes,
                               @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String messageKey, Object... args) {
    holder.registerProblem(anchor,
                           JavaAnalysisBundle.message(messageKey, args),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  private static boolean isNullableNotNullCollectionConflict(@Nullable PsiType expectedType,
                                                             @Nullable PsiType assignedType,
                                                             @NotNull PsiFile place,
                                                             @NotNull Set<? super Couple<PsiType>> visited) {
    if (!visited.add(Couple.of(expectedType, assignedType))) return false;

    GlobalSearchScope scope = place.getResolveScope();
    if (isNullityConflict(JavaGenericsUtil.getCollectionItemType(expectedType, scope),
                          JavaGenericsUtil.getCollectionItemType(assignedType, scope))) {
      return true;
    }

    for (int i = 0; i <= 1; i++) {
      PsiType expectedArg = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
      PsiType assignedArg = PsiUtil.substituteTypeParameter(assignedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
      if (isNullityConflict(expectedArg, assignedArg) ||
          expectedArg != null && assignedArg != null && isNullableNotNullCollectionConflict(expectedArg, assignedArg, place, visited)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isNullityConflict(PsiType expected, PsiType assigned) {
    return DfaPsiUtil.getTypeNullability(expected) == Nullability.NOT_NULL && DfaPsiUtil.getTypeNullability(assigned) == Nullability.NULLABLE;
  }

  @Nullable
  private @InspectionMessage String checkIndirectInheritance(PsiElement psiClass, PsiClass intf) {
    for (PsiMethod intfMethod : intf.getAllMethods()) {
      PsiClass intfMethodClass = intfMethod.getContainingClass();
      PsiMethod overridingMethod = intfMethodClass == null ? null :
                                   JavaOverridingMethodsSearcher.findOverridingMethod((PsiClass)psiClass, intfMethod, intfMethodClass);
      PsiClass overridingMethodClass = overridingMethod == null ? null : overridingMethod.getContainingClass();
      if (overridingMethodClass != null && overridingMethodClass != psiClass) {
        String error = checkIndirectInheritance(intfMethod, intfMethodClass, overridingMethod, overridingMethodClass);
        if (error != null) return error;
      }
    }
    return null;
  }

  @Nullable
  private @InspectionMessage String checkIndirectInheritance(PsiMethod intfMethod,
                                                             PsiClass intfMethodClass,
                                                             PsiMethod overridingMethod,
                                                             PsiClass overridingMethodClass) {
    if (isNullableOverridingNotNull(Annotated.from(overridingMethod), intfMethod)) {
      return JavaAnalysisBundle.message("inspection.message.nullable.method.implements.non.null.method",
                                        overridingMethod.getName(), overridingMethodClass.getName(), intfMethodClass.getName());
    }
    if (isNonAnnotatedOverridingNotNull(overridingMethod, intfMethod)) {
      return JavaAnalysisBundle.message("inspection.message.non.annotated.method.implements.non.null.method",
                                        overridingMethod.getName(), overridingMethodClass.getName(), intfMethodClass.getName());
    }

    PsiParameter[] overridingParameters = overridingMethod.getParameterList().getParameters();
    PsiParameter[] superParameters = intfMethod.getParameterList().getParameters();
    if (overridingParameters.length == superParameters.length) {
      NullableNotNullManager manager = getNullityManager(intfMethod);
      for (int i = 0; i < overridingParameters.length; i++) {
        PsiParameter parameter = overridingParameters[i];
        List<PsiParameter> supers = Collections.singletonList(superParameters[i]);
        if (findNullableSuperForNotNullParameter(parameter, supers) != null) {
          return JavaAnalysisBundle.message("inspection.message.non.null.parameter.should.not.override.nullable.parameter",
                                            parameter.getName(), overridingMethod.getName(), overridingMethodClass.getName(), intfMethodClass.getName());
        }
        if (findNotNullSuperForNonAnnotatedParameter(manager, parameter, supers) != null) {
          return JavaAnalysisBundle.message("inspection.message.non.annotated.parameter.should.not.override.non.null.parameter",
                                            parameter.getName(), overridingMethod.getName(), overridingMethodClass.getName(), intfMethodClass.getName());
        }
        if (isNotNullParameterOverridingNonAnnotated(manager, parameter, supers)) {
          return JavaAnalysisBundle.message("inspection.message.non.null.parameter.should.not.override.non.annotated.parameter",
                                            parameter.getName(), overridingMethod.getName(), overridingMethodClass.getName(), intfMethodClass.getName());
        }
      }
    }

    return null;
  }

  private void checkMethodReference(PsiMethodReferenceExpression expression, @NotNull ProblemsHolder holder) {
    PsiMethod superMethod = LambdaUtil.getFunctionalInterfaceMethod(expression);
    PsiMethod targetMethod = tryCast(expression.resolve(), PsiMethod.class);
    if (superMethod == null || targetMethod == null) return;

    PsiElement refName = expression.getReferenceNameElement();
    assert refName != null;
    if (isNullableOverridingNotNull(check(targetMethod, holder, expression.getType()), superMethod)) {
      reportProblem(holder, refName, "inspection.nullable.problems.Nullable.method.overrides.NotNull",
                    getPresentableAnnoName(targetMethod), getPresentableAnnoName(superMethod));
    }
  }

  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return null;
  }

  private static boolean nullabilityAnnotationsNotAvailable(final PsiFile file) {
    final Project project = file.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return ContainerUtil.find(NullableNotNullManager.getInstance(project).getNullables(), s -> facade.findClass(s, scope) != null) == null;
  }

  private static boolean checkNonStandardAnnotations(PsiField field,
                                                     Annotated annotated,
                                                     String anno, @NotNull ProblemsHolder holder) {
    if (!AnnotationUtil.isAnnotatingApplicable(field, anno)) {
      PsiAnnotation annotation = Objects.requireNonNull(annotated.isDeclaredNullable ? annotated.nullable : annotated.notNull);
      String message = JavaAnalysisBundle.message("inspection.message.code.generation.different.nullability.annotation.will.be.used",
                                                  annotation.getQualifiedName(), anno);
      final PsiJavaCodeReferenceElement annotationNameReferenceElement = annotation.getNameReferenceElement();
      holder.registerProblem(annotationNameReferenceElement != null && annotationNameReferenceElement.isPhysical() ? annotationNameReferenceElement : field.getNameIdentifier(),
                             message,
                             ProblemHighlightType.WEAK_WARNING,
                             new ChangeNullableDefaultsFix(annotated.notNull, annotated.nullable));
      return false;
    }
    return true;
  }

  private void checkAccessors(PsiField field,
                              Annotated annotated,
                              Project project,
                              NullableNotNullManager manager, final String anno, final List<String> annoToRemove, @NotNull ProblemsHolder holder) {
    String propName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiMethod getter = PropertyUtilBase.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
    final PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
    if (nameIdentifier != null && nameIdentifier.isPhysical()) {
      if (PropertyUtil.getFieldOfGetter(getter) == field) {
        LocalQuickFix getterAnnoFix = new AddAnnotationPsiFix(anno, getter, ArrayUtilRt.toStringArray(annoToRemove));
        if (REPORT_NOT_ANNOTATED_GETTER) {
          if (!hasNullability(manager, getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
            reportProblem(holder, nameIdentifier, getterAnnoFix, "inspection.nullable.problems.annotated.field.getter.not.annotated",
                          getPresentableAnnoName(field));
          }
        }
        if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
            annotated.isDeclaredNullable && isNotNullNotInferred(getter, false, false)) {
          reportProblem(holder, nameIdentifier, getterAnnoFix,
                        "inspection.nullable.problems.annotated.field.getter.conflict", getPresentableAnnoName(field),
                        getPresentableAnnoName(getter));
        }
      }
    }

    final PsiClass containingClass = field.getContainingClass();
    final PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propName, isStatic, false);
    if (setter != null && setter.isPhysical() && PropertyUtil.getFieldOfSetter(setter) == field) {
      final PsiParameter[] parameters = setter.getParameterList().getParameters();
      assert parameters.length == 1 : setter.getText();
      final PsiParameter parameter = parameters[0];
      LOG.assertTrue(parameter != null, setter.getText());
      AddAnnotationPsiFix addAnnoFix = createAddAnnotationFix(anno, annoToRemove, parameter);
      if (REPORT_NOT_ANNOTATED_GETTER && !hasNullability(manager, parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
        final PsiIdentifier parameterName = parameter.getNameIdentifier();
        assertValidElement(setter, parameter, parameterName);
        reportProblem(holder, parameterName, addAnnoFix,
                      "inspection.nullable.problems.annotated.field.setter.parameter.not.annotated", getPresentableAnnoName(field));
      }
      if (PropertyUtil.isSimpleSetter(setter)) {
        if (annotated.isDeclaredNotNull && isNullableNotInferred(parameter, false)) {
          final PsiIdentifier parameterName = parameter.getNameIdentifier();
          assertValidElement(setter, parameter, parameterName);
          reportProblem(holder, parameterName, addAnnoFix,
                        "inspection.nullable.problems.annotated.field.setter.parameter.conflict",
                        getPresentableAnnoName(field), getPresentableAnnoName(parameter));
        }
      }
    }
  }

  @NotNull
  private static AddAnnotationPsiFix createAddAnnotationFix(String anno, List<String> annoToRemove, PsiParameter parameter) {
    return new AddAnnotationPsiFix(anno, parameter, ArrayUtilRt.toStringArray(annoToRemove));
  }

  @Contract("_,_,null -> fail")
  private static void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
    LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
    LOG.assertTrue(parameter.isPhysical(), setter.getText());
  }

  private void checkConstructorParameters(PsiField field,
                                          Annotated annotated,
                                          NullableNotNullManager manager,
                                          String anno, List<String> annoToRemove, @NotNull ProblemsHolder holder) {
    List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
    if (initializers.isEmpty()) return;

    List<PsiParameter> notNullParams = new ArrayList<>();

    boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);

    for (PsiExpression rhs : initializers) {
      if (rhs instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression)rhs).resolve();
        if (isConstructorParameter(target) && target.isPhysical()) {
          PsiParameter parameter = (PsiParameter)target;
          if (REPORT_NOT_ANNOTATED_GETTER && !hasNullability(manager, parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier != null && nameIdentifier.isPhysical()) {
              reportProblem(holder, nameIdentifier, createAddAnnotationFix(anno, annoToRemove, parameter),
                            "inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated",
                            getPresentableAnnoName(field));
              continue;
            }
          }

          if (isFinal && annotated.isDeclaredNullable && isNotNullNotInferred(parameter, false, false)) {
            notNullParams.add(parameter);
          }

        }
      }
    }

    if (notNullParams.size() != initializers.size()) {
      // it's not the case that the field is final and @Nullable and always initialized via @NotNull parameters
      // so there might be other initializers that could justify it being nullable
      // so don't highlight field and constructor parameter annotation inconsistency
      return;
    }

    PsiIdentifier nameIdentifier = field.getNameIdentifier();
    if (nameIdentifier.isPhysical()) {
      reportProblem(holder, nameIdentifier, AddAnnotationPsiFix.createAddNotNullFix(field),
                    "0.field.is.always.initialized.not.null", getPresentableAnnoName(field));
    }
  }

  private static boolean isConstructorParameter(@Nullable PsiElement parameter) {
    return parameter instanceof PsiParameter && psiElement(PsiParameterList.class).withParent(psiMethod().constructor(true)).accepts(parameter.getParent());
  }

  @NotNull
  private static String getPresentableAnnoName(@NotNull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    String name = info == null ? null : info.getAnnotation().getQualifiedName();
    if (name == null) {
      return "???";
    }
    return StringUtil.getShortName(name);
  }

  public static String getPresentableAnnoName(@NotNull PsiAnnotation annotation) {
    return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
  }

  /**
   * @return true if owner has a @NotNull or @Nullable annotation,
   * or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
   */
  private static boolean hasNullability(@NotNull NullableNotNullManager manager, @NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    return info != null && info.getNullability() != Nullability.UNKNOWN && info.getInheritedFrom() == null;
  }

  private static final class Annotated {
    private final boolean isDeclaredNotNull;
    private final boolean isDeclaredNullable;
    @Nullable private final PsiAnnotation notNull;
    @Nullable private final PsiAnnotation nullable;

    private Annotated(@Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
      this.isDeclaredNotNull = notNull != null;
      this.isDeclaredNullable = nullable != null;
      this.notNull = notNull;
      this.nullable = nullable;
    }

    @NotNull static Annotated from(@NotNull PsiModifierListOwner owner) {
      NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
      return new Annotated(manager.findExplicitNullabilityAnnotation(owner, Collections.singleton(Nullability.NOT_NULL)),
                           manager.findExplicitNullabilityAnnotation(owner, Collections.singleton(Nullability.NULLABLE)));
    }
  }

  private Annotated check(final PsiModifierListOwner owner, final ProblemsHolder holder, PsiType type) {
    Annotated annotated = Annotated.from(owner);
    PsiAnnotation annotation = annotated.notNull == null ? annotated.nullable : annotated.notNull;
    if (annotation != null && !annotation.isPhysical() && type instanceof PsiPrimitiveType) {
      reportIncorrectLocation(holder, annotation, owner, "inspection.nullable.problems.primitive.type.annotation");
    }
    if (owner instanceof PsiParameter parameter) {
      Nullability expectedNullability = DfaPsiUtil.inferParameterNullability(parameter);
      if (annotated.notNull != null && expectedNullability == Nullability.NULLABLE) {
        reportParameterNullabilityMismatch(parameter, annotated.notNull, holder, "parameter.can.be.null");
      }
      else if (annotated.nullable != null && expectedNullability == Nullability.NOT_NULL) {
        reportParameterNullabilityMismatch(parameter, annotated.nullable, holder, "parameter.is.always.not.null");
      }
    }
    return annotated;
  }

  private void reportParameterNullabilityMismatch(@NotNull PsiParameter owner,
                                                  @NotNull PsiAnnotation annotation,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String messageKey) {
    if (annotation.isPhysical() && !PsiTreeUtil.isAncestor(owner, annotation, true)) return;
    PsiElement anchor = annotation.isPhysical() ? annotation : owner.getNameIdentifier();
    if (anchor != null && !anchor.getTextRange().isEmpty()) {
      reportProblem(holder, anchor, new RemoveAnnotationQuickFix(annotation, owner), messageKey);
    }
  }

  private void reportIncorrectLocation(ProblemsHolder holder, PsiAnnotation annotation,
                                              @Nullable PsiModifierListOwner listOwner,
                                              @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String messageKey,
                                              @NotNull LocalQuickFix @NotNull ... additionalFixes) {
    RemoveAnnotationQuickFix removeFix = new RemoveAnnotationQuickFix(annotation, listOwner, true);
    MoveAnnotationToBoundFix moveToBoundFix = MoveAnnotationToBoundFix.create(annotation);
    LocalQuickFix[] fixes = moveToBoundFix == null ? new LocalQuickFix[]{removeFix} : new LocalQuickFix[]{moveToBoundFix, removeFix};
    reportProblem(holder, !annotation.isPhysical() && listOwner != null ? listOwner.getNavigationElement() : annotation,
                  ArrayUtil.mergeArrays(additionalFixes, fixes), messageKey);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "NullableProblems";
  }

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder) {
    Annotated annotated = check(method, holder, method.getReturnType());

    List<PsiMethod> superMethods = ContainerUtil.map(
      method.findSuperMethodSignaturesIncludingStatic(true), MethodSignatureBackedByPsiMethod::getMethod);

    final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());

    checkSupers(method, holder, annotated, superMethods);
    checkParameters(method, holder, superMethods, nullableManager);
    checkOverriders(method, holder, annotated, nullableManager);
  }

  private void checkSupers(PsiMethod method,
                           ProblemsHolder holder,
                           Annotated annotated,
                           List<? extends PsiMethod> superMethods) {
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    for (PsiMethod superMethod : superMethods) {
      if (isNullableOverridingNotNull(annotated, superMethod)) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, getNullityManager(method).getNullables(), true);
        reportProblem(holder, annotation != null ? annotation : identifier,
                      "inspection.nullable.problems.Nullable.method.overrides.NotNull",
                      getPresentableAnnoName(method), getPresentableAnnoName(superMethod));
        break;
      }

      if (isNonAnnotatedOverridingNotNull(method, superMethod)) {
        reportProblem(holder, identifier, createFixForNonAnnotatedOverridesNotNull(method, superMethod),
                      "inspection.nullable.problems.method.overrides.NotNull", getPresentableAnnoName(superMethod));
        break;
      }

      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null &&
          isNullableNotNullCollectionConflict(superMethod.getReturnType(), method.getReturnType(), holder.getFile(), new HashSet<>())) {
        reportProblem(holder, returnTypeElement, "nullable.stuff.error.overriding.notnull.with.nullable");
        break;
      }
    }
  }

  private static NullableNotNullManager getNullityManager(PsiMethod method) {
    return NullableNotNullManager.getInstance(method.getProject());
  }

  @Nullable
  private static LocalQuickFix createFixForNonAnnotatedOverridesNotNull(PsiMethod method,
                                                                        PsiMethod superMethod) {
    NullableNotNullManager nullableManager = getNullityManager(method);
    return AnnotationUtil.isAnnotatingApplicable(method, nullableManager.getDefaultNotNull())
                              ? AddAnnotationPsiFix.createAddNotNullFix(method)
                              : createChangeDefaultNotNullFix(nullableManager, superMethod);
  }

  private boolean isNullableOverridingNotNull(Annotated methodInfo, PsiMethod superMethod) {
    return REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && methodInfo.isDeclaredNullable && isNotNullNotInferred(superMethod, true, false);
  }

  private boolean isNonAnnotatedOverridingNotNull(PsiMethod method, PsiMethod superMethod) {
    if (REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL &&
        !(method.getReturnType() instanceof PsiPrimitiveType) &&
        !method.isConstructor()) {
      NullableNotNullManager manager = getNullityManager(method);
      NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
      if ((info == null || info.isInferred() ||
           (info.getInheritedFrom() != null && manager.findContainerAnnotation(method) == null)) &&
          isNotNullNotInferred(superMethod, true, IGNORE_EXTERNAL_SUPER_NOTNULL) &&
          !hasInheritableNotNull(superMethod)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasInheritableNotNull(PsiModifierListOwner owner) {
    return AnnotationUtil.isAnnotated(owner, "javax.annotation.constraints.NotNull", CHECK_HIERARCHY | CHECK_TYPE);
  }

  private void checkParameters(PsiMethod method,
                               ProblemsHolder holder,
                               List<? extends PsiMethod> superMethods,
                               NullableNotNullManager nullableManager) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getType() instanceof PsiPrimitiveType) continue;

      List<PsiParameter> superParameters = getSuperParameters(superMethods, parameters, i);

      checkSuperParameterAnnotations(holder, nullableManager, parameter, superParameters);

      checkNullLiteralArgumentOfNotNullParameterUsages(method, holder, nullableManager, i, parameter);
    }
  }

  @NotNull
  private static List<PsiParameter> getSuperParameters(List<? extends PsiMethod> superMethods, PsiParameter[] parameters, int i) {
    List<PsiParameter> superParameters = new ArrayList<>();
    for (PsiMethod superMethod : superMethods) {
      PsiParameter[] _superParameters = superMethod.getParameterList().getParameters();
      if (_superParameters.length == parameters.length) {
        superParameters.add(_superParameters[i]);
      }
    }
    return superParameters;
  }

  private void checkSuperParameterAnnotations(ProblemsHolder holder,
                                              NullableNotNullManager nullableManager,
                                              PsiParameter parameter,
                                              List<PsiParameter> superParameters) {
    PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
    if (nameIdentifier == null) return;
    PsiParameter nullableSuper = findNullableSuperForNotNullParameter(parameter, superParameters);
    if (nullableSuper != null) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, nullableManager.getNotNulls(), true);
      reportProblem(holder, annotation != null ? annotation : nameIdentifier,
                    "inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
                    getPresentableAnnoName(parameter), getPresentableAnnoName(nullableSuper));
    }
    PsiParameter notNullSuper = findNotNullSuperForNonAnnotatedParameter(nullableManager, parameter, superParameters);
    if (notNullSuper != null) {
      LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, nullableManager.getDefaultNotNull())
                          ? AddAnnotationPsiFix.createAddNotNullFix(parameter)
                          : createChangeDefaultNotNullFix(nullableManager, notNullSuper);
      reportProblem(holder, nameIdentifier, fix,
                    "inspection.nullable.problems.parameter.overrides.NotNull", getPresentableAnnoName(notNullSuper));
    }
    if (isNotNullParameterOverridingNonAnnotated(nullableManager, parameter, superParameters)) {
      NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
      assert info != null;
      PsiAnnotation notNullAnnotation = info.getAnnotation();
      boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
      final LocalQuickFix fix = physical ? new RemoveAnnotationQuickFix(notNullAnnotation, parameter) : null;
      reportProblem(holder, physical ? notNullAnnotation : nameIdentifier, fix,
                    "inspection.nullable.problems.NotNull.parameter.overrides.not.annotated", getPresentableAnnoName(parameter));
    }

    PsiTypeElement typeElement = parameter.getTypeElement();
    if (typeElement != null) {
      for (PsiParameter superParameter : superParameters) {
        if (isNullableNotNullCollectionConflict(parameter.getType(), superParameter.getType(), holder.getFile(), new HashSet<>())) {
          reportProblem(holder, typeElement, "nullable.stuff.error.overriding.nullable.with.notnull");
          break;
        }
      }
    }
  }

  @Nullable
  private PsiParameter findNotNullSuperForNonAnnotatedParameter(NullableNotNullManager nullableManager,
                                                                PsiParameter parameter,
                                                                List<? extends PsiParameter> superParameters) {
    return REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !hasNullability(nullableManager, parameter)
           ? ContainerUtil.find(superParameters,
                                sp -> isNotNullNotInferred(sp, false, IGNORE_EXTERNAL_SUPER_NOTNULL) && !hasInheritableNotNull(sp))
           : null;
  }

  @Nullable
  private PsiParameter findNullableSuperForNotNullParameter(PsiParameter parameter, List<? extends PsiParameter> superParameters) {
    return REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && isNotNullNotInferred(parameter, false, false)
           ? ContainerUtil.find(superParameters, sp -> isNullableNotInferred(sp, false))
           : null;
  }

  private boolean isNotNullParameterOverridingNonAnnotated(NullableNotNullManager nullableManager,
                                                           PsiParameter parameter,
                                                           List<? extends PsiParameter> superParameters) {
    if (!REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED) return false;
    NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
    return info != null && info.getNullability() == Nullability.NOT_NULL && !info.isInferred() &&
           ContainerUtil.exists(superParameters, sp -> isSuperNotAnnotated(nullableManager, parameter, sp));
  }

  private static boolean isSuperNotAnnotated(NullableNotNullManager nullableManager, PsiParameter parameter, PsiParameter superParameter) {
    if (hasNullability(nullableManager, superParameter)) return false;
    PsiType type = superParameter.getType();
    if (TypeUtils.isTypeParameter(type)) {
      PsiClass childClass = PsiUtil.getContainingClass(parameter);
      PsiClass superClass = PsiUtil.getContainingClass(superParameter);
      if (superClass != null && childClass != null) {
        PsiType substituted =
          TypeConversionUtil.getSuperClassSubstitutor(superClass, childClass, PsiSubstitutor.EMPTY).substitute(type);
        return DfaPsiUtil.getTypeNullability(substituted) == Nullability.UNKNOWN;
      }
    }
    return true;
  }

  private void checkNullLiteralArgumentOfNotNullParameterUsages(PsiMethod method,
                                                                ProblemsHolder holder,
                                                                NullableNotNullManager nullableManager,
                                                                int parameterIdx,
                                                                PsiParameter parameter) {
    if (!REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER || !holder.isOnTheFly()) return;

    PsiVariable owner = parameter.isPhysical() ? parameter : JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter);
    if (owner == null) return;

    PsiElement elementToHighlight = null;
    NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(owner);
    if (info != null && !info.isInferred()) {
      if (info.getNullability() == Nullability.NOT_NULL) {
        PsiAnnotation notNullAnnotation = info.getAnnotation();
        boolean physical = PsiTreeUtil.isAncestor(owner, notNullAnnotation, true);
        elementToHighlight = physical ? notNullAnnotation : owner.getNameIdentifier();
      }
    }
    else {
      info = DfaPsiUtil.getTypeNullabilityInfo(owner.getType());
      if (info != null && info.getNullability() == Nullability.NOT_NULL) {
        elementToHighlight = owner.getNameIdentifier();
      }
    }
    if (elementToHighlight == null || !JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) return;

    reportProblem(holder, elementToHighlight, createNavigateToNullParameterUsagesFix(parameter),
                  "inspection.nullable.problems.NotNull.parameter.receives.null.literal",
                  StringUtil.getShortName(Objects.requireNonNull(info.getAnnotation().getQualifiedName())));
  }

  private void checkOverriders(@NotNull PsiMethod method,
                               @NotNull ProblemsHolder holder,
                               @NotNull Annotated annotated,
                               @NotNull NullableNotNullManager nullableManager) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] checkParameter = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        checkParameter[i] = isNotNullNotInferred(parameter, false, false) &&
                                !hasInheritableNotNull(parameter) &&
                                !(parameter.getType() instanceof PsiPrimitiveType);
        hasAnnotatedParameter |= checkParameter[i];
      }
      boolean checkReturnType = annotated.isDeclaredNotNull && !hasInheritableNotNull(method) && !(method.getReturnType() instanceof PsiPrimitiveType);
      if (hasAnnotatedParameter || checkReturnType) {
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final boolean superMethodApplicable = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull);
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (shouldSkipOverriderAsGenerated(overriding)) continue;

          if (!methodQuickFixSuggested
              && checkReturnType
              && !isNotNullNotInferred(overriding, false, false)
              && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))
              && AddAnnotationPsiFix.isAvailable(overriding, defaultNotNull)) {
            PsiIdentifier identifier = method.getNameIdentifier();//load tree
            NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(method);
            if (info != null) {
              PsiAnnotation annotation = info.getAnnotation();
              final String[] annotationsToRemove = ArrayUtilRt.toStringArray(nullableManager.getNullables());

              LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(overriding, defaultNotNull)
                                  ? new MyAnnotateMethodFix(defaultNotNull, annotationsToRemove)
                                  : superMethodApplicable ? null : createChangeDefaultNotNullFix(nullableManager, method);

              PsiElement psiElement = annotation;
              if (!annotation.isPhysical() || !PsiTreeUtil.isAncestor(method, annotation, true)) {
                psiElement = identifier;
                if (psiElement == null) continue;
              }
              reportProblem(holder, psiElement, fix, "nullable.stuff.problems.overridden.methods.are.not.annotated");
              methodQuickFixSuggested = true;
            }
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (checkParameter[i] &&
                  !isNotNullNotInferred(parameter, false, false) &&
                  !isNullableNotInferred(parameter, false) &&
                  AddAnnotationPsiFix.isAvailable(parameter, defaultNotNull)) {
                PsiIdentifier identifier = parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameters[i]);
                PsiElement psiElement = info == null ? null : info.getAnnotation();
                if (psiElement == null || !psiElement.isPhysical()) {
                  psiElement = identifier;
                  if (psiElement == null) continue;
                }
                LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, defaultNotNull)
                                    ? new AnnotateOverriddenMethodParameterFix(Nullability.NOT_NULL, defaultNotNull)
                                    : createChangeDefaultNotNullFix(nullableManager, parameters[i]);
                reportProblem(holder, psiElement, fix,
                              "nullable.stuff.problems.overridden.method.parameters.are.not.annotated");
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  public static boolean shouldSkipOverriderAsGenerated(PsiMethod overriding) {
    if (Registry.is("idea.report.nullity.missing.in.generated.overriders")) return false;

    PsiFile file = overriding.getContainingFile();
    VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
    return virtualFile != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, overriding.getProject());
  }

  private static boolean isNotNullNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean skipExternal) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info == null || info.isInferred() || info.getNullability() != Nullability.NOT_NULL) return false;
    if (!checkBases && info.getInheritedFrom() != null) return false;
    if (skipExternal && info.isExternal()) return false;
    return true;
  }

  public static boolean isNullableNotInferred(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    return info != null && !info.isInferred() && info.getNullability() == Nullability.NULLABLE &&
           (checkBases || info.getInheritedFrom() == null);
  }

  private static LocalQuickFix createChangeDefaultNotNullFix(NullableNotNullManager nullableManager, PsiModifierListOwner modifierListOwner) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, nullableManager.getNotNulls());
    if (annotation != null) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        JavaResolveResult resolveResult = referenceElement.advancedResolve(false);
        if (resolveResult.getElement() != null &&
            resolveResult.isValidResult() &&
            !nullableManager.getDefaultNotNull().equals(annotation.getQualifiedName())) {
          return new ChangeNullableDefaultsFix(annotation.getQualifiedName(), null);
        }
      }
    }
    return null;
  }

  private static class MyAnnotateMethodFix implements LocalQuickFix {
    protected final String myAnnotation;
    private final String[] myAnnotationsToRemove;

    MyAnnotateMethodFix(@NotNull String fqn, String @NotNull ... annotationsToRemove) {
      myAnnotation = fqn;
      myAnnotationsToRemove = annotationsToRemove.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : annotationsToRemove;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();

      PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
      if (method == null) return;
      final List<PsiMethod> toAnnotate = new ArrayList<>();

      if (!AnnotateOverriddenMethodParameterFix.processModifiableInheritorsUnderProgress(method, (Consumer<? super PsiMethod>)psiMethod -> {
        if (AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) &&
            !AnnotationUtil.isAnnotated(psiMethod, myAnnotation, CHECK_EXTERNAL | CHECK_TYPE)) {
          toAnnotate.add(psiMethod);
        }
      })) {
        return;
      }

      FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
      for (PsiMethod psiMethod : toAnnotate) {
        AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, psiMethod, myAnnotationsToRemove);
        fix.invoke(psiMethod.getProject(), psiMethod.getContainingFile(), psiMethod, psiMethod);
      }
      UndoUtil.markPsiFileForUndo(method.getContainingFile());
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public @NotNull String getName() {
      return JavaAnalysisBundle.message("inspection.annotate.overridden.method.nullable.quickfix.name",
                                        ClassUtil.extractClassName(myAnnotation));
    }
  }
}

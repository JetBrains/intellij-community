// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavacQuirksInspectionVisitor extends JavaElementVisitor {
  private static final ElementPattern<PsiElement> QUALIFIER_REFERENCE =
    psiElement().withParent(PsiJavaCodeReferenceElement.class).withSuperParent(2, PsiJavaCodeReferenceElement.class);

  private final ProblemsHolder myHolder;
  private final LanguageLevel myLanguageLevel;
  private final JavaSdkVersion myJavaSdkVersion;

  public JavacQuirksInspectionVisitor(ProblemsHolder holder) {
    myHolder = holder;
    PsiFile file = myHolder.getFile();
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myJavaSdkVersion = ObjectUtils
      .notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodRef) {
    PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
    PsiClass targetClass = LambdaCanBeMethodReferenceInspection.getInaccessibleMethodReferenceClass(methodRef, method);
    if (targetClass == null) return;
    String className = PsiFormatUtil.formatClass(targetClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
    myHolder.registerProblem(methodRef,
                             JavaAnalysisBundle.message("inspection.quirk.method.reference.return.type.message", className));
  }

  @Override
  public void visitAnnotationArrayInitializer(final @NotNull PsiArrayInitializerMemberValue initializer) {
    if (PsiUtil.isLanguageLevel7OrHigher(initializer)) return;
    final PsiElement lastElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(initializer.getLastChild());
    if (PsiUtil.isJavaToken(lastElement, JavaTokenType.COMMA)) {
      final String message = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.problem");
      final String fixName = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.fix");
      myHolder.registerProblem(lastElement, message, QuickFixFactory.getInstance().createDeleteFix(lastElement, fixName));
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull PsiTypeParameterList list) {
    if (PsiUtil.isLanguageLevel7OrHigher(list)) return;
    PsiTypeParameter[] parameters = list.getTypeParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter typeParameter = parameters[i];
      for (PsiJavaCodeReferenceElement referenceElement : typeParameter.getExtendsList().getReferenceElements()) {
        PsiElement resolve = referenceElement.resolve();
        if (resolve instanceof PsiTypeParameter && ArrayUtilRt.find(parameters, resolve) > i) {
          myHolder.registerProblem(referenceElement,
                                   JavaAnalysisBundle.message("inspection.compiler.javac.quirks.illegal.forward.reference"));
        }
      }
    }
  }

  @Override
  public void visitTypeCastExpression(final @NotNull PsiTypeCastExpression expression) {
    if (PsiUtil.isLanguageLevel7OrHigher(expression)) return;
    final PsiTypeElement type = expression.getCastType();
    if (type != null) {
      type.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceParameterList(final @NotNull PsiReferenceParameterList list) {
          super.visitReferenceParameterList(list);
          if (list.getFirstChild() != null && QUALIFIER_REFERENCE.accepts(list)) {
            final String message = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.problem");
            final String fixName = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.fix");
            myHolder.registerProblem(list, message, QuickFixFactory.getInstance().createDeleteFix(list, fixName));
          }
        }
      });
    }
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment);
    final PsiType lType = assignment.getLExpression().getType();
    if (lType == null) return;
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    PsiJavaToken operationSign = assignment.getOperationSign();

    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return;

    if (JavaSdkVersion.JDK_1_6.equals(JavaVersionService.getInstance().getJavaSdkVersion(assignment)) &&
        PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()).equals(lType)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorBundle.message("binary.operator.not.applicable", operatorText,
                                               JavaHighlightUtil.formatType(lType),
                                               JavaHighlightUtil.formatType(rExpression.getType()));

      myHolder.registerProblem(assignment, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceAssignmentOperatorWithAssignmentFix(operationSign.getText()));
    }
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    if (expression.getTypeArguments().length == 0) {
      PsiExpression[] args = expression.getArgumentList().getExpressions();
      JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      if (resolveResult instanceof MethodCandidateInfo) {
        PsiMethod method = ((MethodCandidateInfo)resolveResult).getElement();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        if (PsiUtil.isLanguageLevel8OrHigher(expression) &&
            method.isVarArgs() &&
            method.hasTypeParameters() &&
            args.length > method.getParameterList().getParametersCount() + 50) {
          for (PsiTypeParameter typeParameter : method.getTypeParameters()) {
            if (!PsiTypesUtil.isDenotableType(substitutor.substitute(typeParameter), expression)) {
              return;
            }
          }

          if (isSuspicious(args, method)) {
            myHolder.registerProblem(expression.getMethodExpression(),
                                     JavaAnalysisBundle.message("vararg.method.call.with.50.poly.arguments"),
                                     new MyAddExplicitTypeArgumentsFix());
          }
        }
        if (resolveResult.isValidResult()) {
          for (PsiType value : substitutor.getSubstitutionMap().values()) {
            if (value instanceof PsiIntersectionType) {
              PsiClass aClass = Arrays.stream(((PsiIntersectionType)value).getConjuncts())
                .map(PsiUtil::resolveClassInClassTypeOnly)
                .filter(_aClass -> _aClass != null && _aClass.hasModifierProperty(PsiModifier.FINAL))
                .findFirst().orElse(null);
              if (aClass != null && aClass.hasModifierProperty(PsiModifier.FINAL)) {
                for (PsiType conjunct : ((PsiIntersectionType)value).getConjuncts()) {
                  PsiClass currentClass = PsiUtil.resolveClassInClassTypeOnly(conjunct);
                  if (currentClass != null &&
                      !aClass.equals(currentClass) &&
                      !aClass.isInheritor(currentClass, true)) {
                    final String descriptionTemplate =
                      JavaAnalysisBundle.message("inspection.message.javac.quick.intersection.type.problem",
                                                 value.getPresentableText(), ObjectUtils.notNull(aClass.getQualifiedName(),
                                                                                                 Objects.requireNonNull(aClass.getName())));
                    myHolder.registerProblem(expression.getMethodExpression(), descriptionTemplate);
                  }
                }
                break;
              }
            }
          }
        }
      }
    }
  }

  public static boolean isSuspicious(PsiExpression[] args, PsiMethod method) {
    int count = 0;
    for (int i = method.getParameterList().getParametersCount(); i < args.length; i++) {
      if (PsiPolyExpressionUtil.isPolyExpression(args[i]) && ++count > 50) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_7) && !myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      PsiType ltype = expression.getLOperand().getType();
      PsiExpression rOperand = expression.getROperand();
      if (rOperand != null) {
        PsiType rtype = rOperand.getType();
        if (ltype != null && rtype != null &&
            (ltype.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ^ rtype.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) &&
            (TypeConversionUtil.isPrimitiveAndNotNull(ltype) ^ TypeConversionUtil.isPrimitiveAndNotNull(rtype)) &&
            TypeConversionUtil.isBinaryOperatorApplicable(expression.getOperationTokenType(), ltype, rtype, false) &&
            TypeConversionUtil.areTypesConvertible(rtype, ltype)) {
          myHolder.registerProblem(expression.getOperationSign(), JavaAnalysisBundle
            .message("comparision.between.object.and.primitive"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    }
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    checkHiddenClassReference(ref);
    checkTypeAnnotationModuleDependency(ref);
  }

  private void checkTypeAnnotationModuleDependency(@NotNull PsiJavaCodeReferenceElement ref) {
    // See JDK-8225377 and JDK-8370800
    if (myJavaSdkVersion.getMaxLanguageLevel().isLessThan(LanguageLevel.JDK_22)) return;
    if (!(ref.resolve() instanceof PsiClass target)) return;
    PsiFile targetFile = target.getContainingFile();
    if (targetFile == null || targetFile instanceof PsiCompiledFile) return;
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(ref, PsiTypeElement.class, false, PsiExpression.class, PsiCodeBlock.class);
    if (typeElement == null) return;
    PsiFile file = myHolder.getFile();
    Module module = ModuleUtilCore.findModuleForFile(file);
    if (module == null) return;
    Module targetModule = ModuleUtilCore.findModuleForFile(targetFile);
    if (targetModule == null || targetModule == module) return;
    GlobalSearchScope scope = file.getResolveScope();
    List<RequiredModuleAccess> requiredModuleAccesses = getRequiredModuleAccesses(target);
    for (RequiredModuleAccess access : requiredModuleAccesses) {
      PsiFile requiredFile = access.target.getContainingFile();
      if (requiredFile == null) continue;
      VirtualFile virtualFile = requiredFile.getVirtualFile();
      if (virtualFile == null) continue;
      if (scope.contains(virtualFile)) continue;
      Module requiredModule = ModuleUtilCore.findModuleForFile(requiredFile);
      if (requiredModule == null) continue;
      String targetName = access.target.getName();
      if (targetName == null) continue;
      PsiJavaCodeReferenceElement fakeRef = JavaPsiFacade.getElementFactory(file.getProject()).createReferenceFromText(targetName, ref);
      List<@NotNull LocalQuickFix> fixes = OrderEntryFix.registerFixes(fakeRef, access.target, new ArrayList<>());
      myHolder.registerProblem(ref, JavaAnalysisBundle.message("inspection.quirk.type.annotation.transitive.dependency.message",
                                                               module.getName(), requiredModule.getName(), formatElement(access.member)),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private static @Nls String formatElement(@NotNull PsiNamedElement element) {
    if (element instanceof PsiField || element instanceof PsiMethod) {
      PsiClass psiClass = ((PsiMember)element).getContainingClass();
      if (psiClass != null) {
        String name = psiClass.getName();
        if (name != null) return "'" + name + "." + element.getName() + "'";
      }
    }
    if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
      return JavaAnalysisBundle.message("inspection.quirk.type.annotation.transitive.dependency.parameter.format", parameter.getName(), formatElement(method));
    }
    return "'" + element.getName() + "'";
  }

  private static @NotNull List<@NotNull RequiredModuleAccess> getRequiredModuleAccesses(@NotNull PsiClass target) {
    return CachedValuesManager.getCachedValue(
      target, () -> CachedValueProvider.Result.create(computeRequiredModuleAccesses(target),
                                                      PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @NotNull List<@NotNull RequiredModuleAccess> computeRequiredModuleAccesses(@NotNull PsiClass target) {
    Module module = ModuleUtilCore.findModuleForPsiElement(target);
    if (module == null) return List.of();
    List<RequiredModuleAccess> result = new ArrayList<>();
    for (PsiField field : target.getFields()) {
      result.addAll(ContainerUtil.map(computeRequiredModuleAccesses(module, field.getType()), t -> new RequiredModuleAccess(field, t)));
    }
    for (PsiMethod method : target.getMethods()) {
      result.addAll(ContainerUtil.map(computeRequiredModuleAccesses(module, method.getReturnType()), t -> new RequiredModuleAccess(method, t)));
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        result.addAll(ContainerUtil.map(computeRequiredModuleAccesses(module, parameter.getType()), t -> new RequiredModuleAccess(parameter, t)));
      }
    }
    return result;
  }

  private static @NotNull Set<@NotNull PsiClass> computeRequiredModuleAccesses(@NotNull Module module, @Nullable PsiType type) {
    if (type == null || !hasTypeAnnotations(type)) return Set.of();
    return getGenericClasses(module, type);
  }

  private static @NotNull Set<@NotNull PsiClass> getGenericClasses(@NotNull Module module, @Nullable PsiType type) {
    Set<PsiClass> result = new HashSet<>();
    collectGenericClasses(module, type, result);
    return result;
  }

  private static void collectGenericClasses(@NotNull Module module, @Nullable PsiType type, @NotNull Set<PsiClass> result) {
    if (type == null) return;

    if (type instanceof PsiArrayType arrayType) {
      collectGenericClasses(module, arrayType.getComponentType(), result);
    }
    else if (type instanceof PsiClassType classType) {
      PsiType[] parameters = classType.getParameters();
      if (parameters.length > 0) {
        PsiClass psiClass = classType.resolve();
        if (psiClass != null) {
          Module requiredModule = ModuleUtilCore.findModuleForPsiElement(psiClass);
          if (requiredModule != null && requiredModule != module) {
            result.add(psiClass);
          }
        }
        for (PsiType parameter : parameters) {
          collectGenericClasses(module, parameter, result);
        }
      }
    }
    else if (type instanceof PsiWildcardType wildcardType) {
      PsiType bound = wildcardType.getBound();
      if (bound != null) {
        collectGenericClasses(module, bound, result);
      }
    }
    else if (type instanceof PsiIntersectionType intersectionType) {
      for (PsiType conjunct : intersectionType.getConjuncts()) {
        collectGenericClasses(module, conjunct, result);
      }
    }
  }

  private static boolean hasTypeAnnotations(@NotNull PsiType type) {
    if (type.getAnnotations().length > 0) return true;

    if (type instanceof PsiArrayType arrayType) {
      return hasTypeAnnotations(arrayType.getComponentType());
    }

    if (type instanceof PsiClassType classType) {
      for (PsiType parameter : classType.getParameters()) {
        if (hasTypeAnnotations(parameter)) return true;
      }
    }

    if (type instanceof PsiWildcardType wildcardType) {
      PsiType bound = wildcardType.getBound();
      if (bound != null && hasTypeAnnotations(bound)) return true;
    }

    if (type instanceof PsiIntersectionType intersectionType) {
      for (PsiType conjunct : intersectionType.getConjuncts()) {
        if (hasTypeAnnotations(conjunct)) return true;
      }
    }

    return false;
  }

  record RequiredModuleAccess(@NotNull PsiNamedElement member, @NotNull PsiClass target) {
  }

  private void checkHiddenClassReference(@NotNull PsiJavaCodeReferenceElement ref) {
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) return;//javac 9 has no such bug
    if (ref.getParent() instanceof PsiTypeElement) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (psiClass == null) return;
      if (PsiTreeUtil.isAncestor(psiClass.getExtendsList(), ref, false) ||
          PsiTreeUtil.isAncestor(psiClass.getImplementsList(), ref, false)) {
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).resolve() == psiClass) {
          final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(ref, PsiJavaCodeReferenceElement.class);
          if (referenceElement == null) return;
          final PsiElement typeClass = referenceElement.resolve();
          if (!(typeClass instanceof PsiClass)) return;
          final PsiElement resolve = ref.resolve();
          final PsiClass containingClass = resolve != null ? ((PsiClass)resolve).getContainingClass() : null;
          if (containingClass == null) return;
          PsiClass hiddenClass;
          if (psiClass.isInheritor(containingClass, true)) {
            hiddenClass = (PsiClass)resolve;
          }
          else {
            hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getExtendsList());
            if (hiddenClass == null) {
              hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getImplementsList());
            }
          }
          if (hiddenClass != null) {
            myHolder.registerProblem(ref, JavaErrorBundle.message("text.class.is.not.accessible", hiddenClass.getName()));
          }
        }
      }
    }
  }

  private static PsiClass unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(@NotNull PsiClass containingClass,
                                                                                               @Nullable PsiReferenceList referenceList) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (!referenceElement.isQualified()) {
          final PsiElement superClass = referenceElement.resolve();
          if (superClass instanceof PsiClass) {
            final PsiClass superContainingClass = ((PsiClass)superClass).getContainingClass();
            if (superContainingClass != null &&
                InheritanceUtil.isInheritorOrSelf(containingClass, superContainingClass, true) &&
                !PsiTreeUtil.isAncestor(superContainingClass, containingClass, true)) {
              return (PsiClass)superClass;
            }
          }
        }
      }
    }
    return null;
  }


  private static class ReplaceAssignmentOperatorWithAssignmentFix extends PsiUpdateModCommandQuickFix {
    private final String myOperationSign;

    ReplaceAssignmentOperatorWithAssignmentFix(String operationSign) {
      myOperationSign = operationSign;
    }

    @Override
    public @Nls @NotNull String getName() {
      return JavaAnalysisBundle.message("replace.0.with", myOperationSign);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("replace.operator.assignment.with.assignment");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiAssignmentExpression assignment) {
        PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression(assignment);
      }
    }
  }

  private static class MyAddExplicitTypeArgumentsFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return QuickFixBundle.message("add.type.arguments.single.argument.text");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression call) {
          PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments(call, null);
          if (withArgs == null) return;
          parent.replace(withArgs);
        }
      }
    }
  }
}

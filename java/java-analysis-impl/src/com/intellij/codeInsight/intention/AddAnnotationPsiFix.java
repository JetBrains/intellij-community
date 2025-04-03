// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AddAnnotationPsiFix extends LocalQuickFixOnPsiElement implements LocalQuickFix {
  protected final String myAnnotation;
  final String[] myAnnotationsToRemove;
  @SafeFieldForPreview
  final PsiNameValuePair[] myPairs; // not used when registering local quick fix
  protected final @IntentionName String myText;
  private final AnnotationPlace myAnnotationPlace;
  private final boolean myAvailableInBatchMode;

  /**
   * @deprecated use {@link AddAnnotationModCommandAction} instead
   */
  @Deprecated
  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }

  /**
   * @deprecated use {@link AddAnnotationModCommandAction} instead
   */
  @Deprecated
  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             PsiNameValuePair @NotNull [] values,
                             String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, values, choosePlace(fqn, modifierListOwner), annotationsToRemove);
  }

  /**
   * @deprecated use {@link AddAnnotationModCommandAction} instead
   */
  @Deprecated
  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             PsiNameValuePair @NotNull [] values,
                             @NotNull AnnotationPlace place,
                             String @NotNull ... annotationsToRemove) {
    super(modifierListOwner);
    myAnnotation = fqn;
    ObjectUtils.assertAllElementsNotNull(values);
    myPairs = values;
    ObjectUtils.assertAllElementsNotNull(annotationsToRemove);
    myAnnotationsToRemove = annotationsToRemove;
    myText = calcText(modifierListOwner, myAnnotation);
    myAnnotationPlace = place;
    myAvailableInBatchMode = place == AnnotationPlace.IN_CODE || 
                             place == AnnotationPlace.EXTERNAL && ExternalAnnotationsManager.getInstance(modifierListOwner.getProject()).hasConfiguredAnnotationRoot(modifierListOwner);
  }

  public static @IntentionName String calcText(PsiModifierListOwner modifierListOwner, @Nullable String annotation) {
    final String shortName = annotation == null ? null : annotation.substring(annotation.lastIndexOf('.') + 1);
    if (modifierListOwner instanceof PsiNamedElement) {
      final String name = PsiFormatUtil.formatSimple((PsiNamedElement)modifierListOwner);
      if (name != null) {
        JavaElementKind type = JavaElementKind.fromElement(modifierListOwner).lessDescriptive();
        if (shortName == null) {
          return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.element", type.object(), name);
        }
        return JavaAnalysisBundle
          .message("inspection.i18n.quickfix.annotate.element.as", type.object(), name, shortName);
      }
    }
    if (shortName == null) {
      return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate");
    }
    return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.as", shortName);
  }

  public static @Nullable PsiModifierListOwner getContainer(PsiFile file, int offset) {
    return getContainer(file, offset, false);
  }

  public static @Nullable PsiModifierListOwner getContainer(PsiFile file, int offset, boolean availableOnReference) {
    PsiReference reference = availableOnReference ? file.findReferenceAt(offset) : null;
    if (reference != null) {
      PsiElement target = reference.resolve();
      if (target instanceof PsiMember) {
        return (PsiMember)target;
      }
    }

    PsiElement element = file.findElementAt(offset);

    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (listOwner instanceof PsiParameter) return listOwner;

    if (listOwner instanceof PsiNameIdentifierOwner) {
      PsiElement id = ((PsiNameIdentifierOwner)listOwner).getNameIdentifier();
      if (id != null && id.getTextRange().containsOffset(offset)) { // Groovy methods will pass this check as well
        return listOwner;
      }
    }

    return null;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.annotation.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return isAvailable((PsiModifierListOwner)startElement, myAnnotation);
  }

  public static boolean isAvailable(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String annotationFQN) {
    if (!modifierListOwner.isValid()) return false;
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, modifierListOwner)) return false;

    if (modifierListOwner instanceof PsiParameter parameter && parameter.getTypeElement() == null) {
      if (modifierListOwner.getParent() instanceof PsiParameterList list &&
          list.getParent() instanceof PsiLambdaExpression lambda) {
        // Lambda parameter without type cannot be annotated. Check if we can specify types
        if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, modifierListOwner)) return true;
        return LambdaUtil.createLambdaParameterListWithFormalTypes(lambda.getFunctionalInterfaceType(), lambda, false) != null;
      }
      return false;
    }
    // e.g. PsiTypeParameterImpl doesn't have modifier list
    PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null || modifierList instanceof LightElement || modifierListOwner instanceof LightElement) return false;
    PsiClass annotationClass = JavaPsiFacade.getInstance(modifierListOwner.getProject())
      .findClass(annotationFQN, modifierListOwner.getResolveScope());
    boolean hasTypeUse = annotationClass != null &&
                         AnnotationTargetUtil.findAnnotationTarget(annotationClass, PsiAnnotation.TargetType.TYPE_USE) != null;
    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, hasTypeUse);
    if (target == modifierList) {
      return !AnnotationUtil.isAnnotated(modifierListOwner, annotationFQN, CHECK_EXTERNAL | (hasTypeUse ? CHECK_TYPE : 0));
    }
    return target != null && !target.hasAnnotation(annotationFQN);
  }

  @Override
  public boolean startInWriteAction() {
    return myAnnotationPlace == AnnotationPlace.IN_CODE;
  }

  @Override
  public boolean availableInBatchMode() {
    return myAvailableInBatchMode;
  }
  
  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)startElement;
    ActionContext context = ActionContext.from(null, file);
    AddAnnotationModCommandAction delegate = new AddAnnotationModCommandAction(
      myAnnotation, modifierListOwner, myPairs, myAnnotationPlace, myAnnotationsToRemove);
    if (delegate.getPresentation(context) == null) return;
    ModCommandExecutor.executeInteractively(context, getFamilyName(), null, () -> delegate.perform(context));
  }

  public static @NotNull AnnotationPlace choosePlace(@NotNull String annotation, @NotNull PsiModifierListOwner modifierListOwner) {
    Project project = modifierListOwner.getProject();
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    if (BaseIntentionAction.canModify(modifierListOwner)) {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(annotation, modifierListOwner.getResolveScope());
      if (aClass != null) {
        if (JavaPsiAnnotationUtil.getRetentionPolicy(aClass) == RetentionPolicy.RUNTIME) {
          return AnnotationPlace.IN_CODE;
        }
        if (!CommonClassNames.DEFAULT_PACKAGE.equals(StringUtil.getPackageName(annotation))) {
          PsiClass resolvedBySimpleName = JavaPsiFacade.getInstance(project).getResolveHelper()
            .resolveReferencedClass(StringUtil.getShortName(annotation), modifierListOwner);
          if (resolvedBySimpleName != null && resolvedBySimpleName.getManager().areElementsEquivalent(resolvedBySimpleName, aClass)) {
            // if class is already imported in current file
            return AnnotationPlace.IN_CODE;
          }
        }
      }
    }
    return annotationsManager.chooseAnnotationsPlaceNoUi(modifierListOwner);
  }

  /**
   * Add new physical (non-external) annotation to the annotation owner. Annotation will not be added if it already exists
   * on the same annotation owner (externally or explicitly) or if there's a {@link PsiTypeElement} that follows the owner,
   * and its innermost component type has the annotation with the same fully-qualified name.
   * E.g. the method like {@code java.lang.@Foo String[] getStringArray()} will not be annotated with another {@code @Foo}
   * annotation.
   *
   * @param fqn fully-qualified annotation name
   * @param pairs name/value pairs for the new annotation (not changed by this method,
   *              could be result of {@link PsiAnnotationParameterList#getAttributes()} of existing annotation).
   * @param owner an owner object to add the annotation to ({@link PsiModifierList} or {@link PsiType}).
   * @return added physical annotation; null if annotation already exists (in this case, no changes are performed)
   */
  public static @Nullable PsiAnnotation addPhysicalAnnotationIfAbsent(@NotNull String fqn,
                                                            @NotNull PsiNameValuePair @NotNull [] pairs,
                                                            @NotNull PsiAnnotationOwner owner) {
    if (owner.hasAnnotation(fqn)) return null;
    if (owner instanceof PsiModifierList) {
      PsiElement modListOwner = ((PsiModifierList)owner).getParent();
      if (modListOwner instanceof PsiModifierListOwner) {
        if (ExternalAnnotationsManager.getInstance(modListOwner.getProject())
              .findExternalAnnotation((PsiModifierListOwner)modListOwner, fqn) != null) {
          return null;
        }
        PsiTypeElement typeElement = modListOwner instanceof PsiMethod ? ((PsiMethod)modListOwner).getReturnTypeElement() :
                                     modListOwner instanceof PsiVariable ? ((PsiVariable)modListOwner).getTypeElement() : null;
        while (typeElement != null && typeElement.getType() instanceof PsiArrayType) {
          typeElement = PsiTreeUtil.getChildOfType(typeElement, PsiTypeElement.class);
        }
        if (typeElement != null && typeElement.getType().hasAnnotation(fqn)) {
          return null;
        }
      }
    }
    return addPhysicalAnnotationTo(expandParameterAndAddAnnotation(owner, fqn), pairs);
  }

  @SuppressWarnings("unused") // for backwards compatibility
  public static PsiAnnotation addPhysicalAnnotationTo(String fqn, PsiNameValuePair[] pairs, PsiAnnotationOwner owner) {
    return addPhysicalAnnotationTo(expandParameterAndAddAnnotation(owner, fqn), pairs);
  }

  public static PsiAnnotation addPhysicalAnnotationTo(PsiAnnotation inserted, PsiNameValuePair[] pairs) {
    for (PsiNameValuePair pair : pairs) {
      inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
    }
    return inserted;
  }

  protected @Nullable PsiAnnotation addAnnotation(PsiAnnotationOwner annotationOwner, String fqn) {
    return expandParameterAndAddAnnotation(annotationOwner, fqn);
  }

  public static @NotNull PsiAnnotation expandParameterAndAddAnnotation(PsiAnnotationOwner annotationOwner, String fqn) {
    if (annotationOwner instanceof PsiModifierList) {
      annotationOwner = expandParameterIfNecessary((PsiModifierList)annotationOwner);
    }
    return annotationOwner.addAnnotation(fqn);
  }

  public static @NotNull PsiModifierList expandParameterIfNecessary(PsiModifierList owner) {
    PsiParameter parameter = ObjectUtils.tryCast(owner.getParent(), PsiParameter.class);
    if (parameter != null && parameter.getTypeElement() == null) {
      PsiParameterList list = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
      if (list != null && list.getParent() instanceof PsiLambdaExpression) {
        PsiParameter[] parameters = list.getParameters();
        int index = ArrayUtil.indexOf(parameters, parameter);
        PsiParameterList newList;
        if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, list)) {
          String newListText = StreamEx.of(parameters).map(p -> JavaKeywords.VAR + " " + p.getName()).joining(",", "(", ")");
          newList = ((PsiLambdaExpression)JavaPsiFacade.getElementFactory(list.getProject())
            .createExpressionFromText(newListText+" -> {}", null)).getParameterList();
          newList = (PsiParameterList)new CommentTracker().replaceAndRestoreComments(list, newList);
        } else {
          newList = LambdaUtil.specifyLambdaParameterTypes((PsiLambdaExpression)list.getParent());
        }
        if (newList != null) {
          list = newList;
          parameter = list.getParameter(index);
          LOG.assertTrue(parameter != null);
          owner = parameter.getModifierList();
          LOG.assertTrue(owner != null);
        }
      }
    }
    return owner;
  }

  public static void removePhysicalAnnotations(@NotNull PsiModifierListOwner owner, String @NotNull ... fqns) {
    for (String fqn : fqns) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, true, fqn);
      if (annotation != null && !AnnotationUtil.isInferredAnnotation(annotation)) {
        new CommentTracker().deleteAndRestoreComments(annotation);
      }
    }
  }

  protected String @NotNull [] getAnnotationsToRemove() {
    return myAnnotationsToRemove;
  }

  public static boolean isNullabilityAnnotationApplicable(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiMethod) {
      PsiType returnType = ((PsiMethod)owner).getReturnType();
      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }
    return !(owner instanceof PsiClass);
  }
}

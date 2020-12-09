// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AddAnnotationPsiFix extends LocalQuickFixOnPsiElement {
  protected final String myAnnotation;
  final String[] myAnnotationsToRemove;
  @SafeFieldForPreview
  final PsiNameValuePair[] myPairs; // not used when registering local quick fix
  protected final @IntentionName String myText;
  private final ExternalAnnotationsManager.AnnotationPlace myAnnotationPlace;

  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }
  
  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             PsiNameValuePair @NotNull [] values,
                             String @NotNull ... annotationsToRemove) {
    super(modifierListOwner);
    myAnnotation = fqn;
    ObjectUtils.assertAllElementsNotNull(values);
    myPairs = values;
    ObjectUtils.assertAllElementsNotNull(annotationsToRemove);
    myAnnotationsToRemove = annotationsToRemove;
    myText = calcText(modifierListOwner, myAnnotation);
    myAnnotationPlace = choosePlace(modifierListOwner);
  }

  public static @IntentionName String calcText(PsiModifierListOwner modifierListOwner, @Nullable String annotation) {
    final String shortName = annotation == null ? null : annotation.substring(annotation.lastIndexOf('.') + 1);
    if (modifierListOwner instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)modifierListOwner).getName();
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
    if (!PsiUtil.isLanguageLevel5OrHigher(modifierListOwner)) return false;

    if (modifierListOwner instanceof PsiParameter && ((PsiParameter)modifierListOwner).getTypeElement() == null) {
      if (modifierListOwner.getParent() instanceof PsiParameterList &&
          modifierListOwner.getParent().getParent() instanceof PsiLambdaExpression) {
        // Lambda parameter without type cannot be annotated. Check if we can specify types
        if (PsiUtil.isLanguageLevel11OrHigher(modifierListOwner)) return true;
        PsiLambdaExpression lambda = (PsiLambdaExpression)modifierListOwner.getParent().getParent();
        return LambdaUtil.createLambdaParameterListWithFormalTypes(lambda.getFunctionalInterfaceType(), lambda, false) != null;
      }
      return false;
    }
    // e.g. PsiTypeParameterImpl doesn't have modifier list
    PsiModifierList modifierList = modifierListOwner.getModifierList();
    return modifierList != null
           && !(modifierList instanceof LightElement)
           && !(modifierListOwner instanceof LightElement)
           && !AnnotationUtil.isAnnotated(modifierListOwner, annotationFQN, CHECK_EXTERNAL | CHECK_TYPE);
  }

  @Override
  public boolean startInWriteAction() {
    return myAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiModifierListOwner myModifierListOwner = (PsiModifierListOwner)startElement;

    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(myModifierListOwner, myAnnotation);
    if (target == null || ContainerUtil.exists(target.getApplicableAnnotations(), anno -> anno.hasQualifiedName(myAnnotation))) {
      return;
    }
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    ExternalAnnotationsManager.AnnotationPlace place = myAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NEED_ASK_USER ?
                                                       annotationsManager.chooseAnnotationsPlace(myModifierListOwner) : myAnnotationPlace;
    switch (place) {
      case NOWHERE:
        return;
      case EXTERNAL:
        for (String fqn : myAnnotationsToRemove) {
          annotationsManager.deannotate(myModifierListOwner, fqn);
        }
        try {
          annotationsManager.annotateExternally(myModifierListOwner, myAnnotation, file, myPairs);
        }
        catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
        }
        break;
      case IN_CODE:
        final PsiFile containingFile = myModifierListOwner.getContainingFile();
        Runnable command = () -> {
          removePhysicalAnnotations(myModifierListOwner, myAnnotationsToRemove);

          PsiAnnotation inserted = addPhysicalAnnotationTo(myAnnotation, myPairs, target);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
        };

        if (!containingFile.isPhysical()) {
          command.run();
        }
        else {
          WriteCommandAction.runWriteCommandAction(project, null, null, command, containingFile);
        } 

        if (containingFile != file) {
          UndoUtil.markPsiFileForUndo(file);
        }
        break;
    }
  }

  private @NotNull ExternalAnnotationsManager.AnnotationPlace choosePlace(@NotNull PsiModifierListOwner modifierListOwner) {
    Project project = modifierListOwner.getProject();
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myAnnotation, modifierListOwner.getResolveScope());
    if (aClass != null && BaseIntentionAction.canModify(modifierListOwner)) {
      if (AnnotationsHighlightUtil.getRetentionPolicy(aClass) == RetentionPolicy.RUNTIME) {
        return ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
      }
      if (!CommonClassNames.DEFAULT_PACKAGE.equals(StringUtil.getPackageName(myAnnotation))) {
        PsiClass resolvedBySimpleName = JavaPsiFacade.getInstance(project).getResolveHelper()
          .resolveReferencedClass(StringUtil.getShortName(myAnnotation), modifierListOwner);
        if (resolvedBySimpleName != null && resolvedBySimpleName.getManager().areElementsEquivalent(resolvedBySimpleName, aClass)) {
          // if class is already imported in current file
          return ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
        }
      }
    }
    return annotationsManager.chooseAnnotationsPlaceNoUi(modifierListOwner);
  }

  /**
   * @deprecated use {@link #addPhysicalAnnotationIfAbsent(String, PsiNameValuePair[], PsiAnnotationOwner)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static PsiAnnotation addPhysicalAnnotation(String fqn, PsiNameValuePair[] pairs, PsiModifierList modifierList) {
    return addPhysicalAnnotationTo(fqn, pairs, modifierList);
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
  @Nullable
  public static PsiAnnotation addPhysicalAnnotationIfAbsent(@NotNull String fqn,
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
    return addPhysicalAnnotationTo(fqn, pairs, owner);
  }

  public static PsiAnnotation addPhysicalAnnotationTo(String fqn, PsiNameValuePair[] pairs, PsiAnnotationOwner owner) {
    owner = expandParameterIfNecessary(owner);
    PsiAnnotation inserted;
    try {
      inserted = owner.addAnnotation(fqn);
    }
    catch (UnsupportedOperationException | IncorrectOperationException e) {
      String message = "Cannot add annotation to "+owner.getClass();
      if (owner instanceof PsiElement) {
        StreamEx.iterate(((PsiElement)owner).getParent(), p -> p != null && !(p instanceof PsiFileSystemItem), PsiElement::getParent)
          .map(p -> p.getClass().getName()).toList();
        message += "; parents: " + message;
      }
      throw new RuntimeException(message, e);
    }
    for (PsiNameValuePair pair : pairs) {
      inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
    }
    return inserted;
  }

  private static PsiAnnotationOwner expandParameterIfNecessary(PsiAnnotationOwner owner) {
    if (owner instanceof PsiModifierList) {
      PsiParameter parameter = ObjectUtils.tryCast(((PsiModifierList)owner).getParent(), PsiParameter.class);
      if (parameter != null && parameter.getTypeElement() == null) {
        PsiParameterList list = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
        if (list != null && list.getParent() instanceof PsiLambdaExpression) {
          PsiParameter[] parameters = list.getParameters();
          int index = ArrayUtil.indexOf(parameters, parameter);
          PsiParameterList newList;
          if (PsiUtil.isLanguageLevel11OrHigher(list)) {
            String newListText = StreamEx.of(parameters).map(p -> PsiKeyword.VAR + " " + p.getName()).joining(",", "(", ")");
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

  /**
   * Creates a fix which will add default "Nullable" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  public static @Nullable AddAnnotationPsiFix createAddNullableFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultNullable(), manager.getNotNulls());
  }

  /**
   * Creates a fix which will add default "NotNull" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  public static @Nullable AddAnnotationPsiFix createAddNotNullFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultNotNull(), manager.getNullables());
  }

  private static @Nullable AddAnnotationPsiFix createAddNullableNotNullFix(PsiModifierListOwner owner, String annotationToAdd,
                                                                           List<String> annotationsToRemove) {
    if (!isNullabilityAnnotationApplicable(owner)) return null;
    return new AddAnnotationPsiFix(annotationToAdd, owner, ArrayUtilRt.toStringArray(annotationsToRemove));
  }
}

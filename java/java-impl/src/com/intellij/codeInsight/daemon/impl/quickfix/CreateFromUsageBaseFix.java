// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

public abstract class CreateFromUsageBaseFix extends BaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiElement element = getElement();
    if (element == null || isValidElement(element)) {
      return false;
    }

    int offset = editor.getCaretModel().getOffset();
    if (!isAvailableImpl(offset)) {
      return false;
    }

    List<PsiClass> targetClasses = filterTargetClasses(element, project);
    return !targetClasses.isEmpty();
  }

  protected abstract boolean isAvailableImpl(int offset);

  protected abstract boolean isValidElement(PsiElement result);

  protected void chooseTargetClass(@NotNull Project project, @NotNull Editor editor, @NotNull Consumer<? super PsiClass> createInClass) {
    PsiElement element = getElement();
    List<PsiClass> targetClasses = filterTargetClasses(element, project);

    if (targetClasses.isEmpty()) return;

    if (targetClasses.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      doInvoke(targetClasses.get(0), createInClass);
    } else {
      chooseTargetClass(targetClasses, editor, createInClass);
    }
  }

  protected @Unmodifiable List<PsiClass> filterTargetClasses(PsiElement element, Project project) {
    return ContainerUtil.filter(getTargetClasses(element), psiClass -> JVMElementFactories.getFactory(psiClass.getLanguage(), project) != null);
  }

  private static void doInvoke(final PsiClass targetClass, Consumer<? super PsiClass> invokeImpl) {
    if (!FileModificationService.getInstance().prepareFileForWrite(targetClass.getContainingFile())) {
      return;
    }

    IdeDocumentHistory.getInstance(targetClass.getProject()).includeCurrentPlaceAsChangePlace();
    ApplicationManager.getApplication().runWriteAction(() -> invokeImpl.accept(targetClass));
  }

  protected abstract @Nullable PsiElement getElement();

  private void chooseTargetClass(List<PsiClass> classes, final Editor editor, Consumer<? super PsiClass> invokeImpl) {
    final PsiClass firstClass = classes.get(0);
    final Project project = firstClass.getProject();

    final PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes, firstClass);
    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    IPopupChooserBuilder<PsiClass> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(classes)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setSelectedValue(preselection, true)
      .setRenderer(renderer)
      .setItemChosenCallback((aClass) -> {
        AnonymousTargetClassPreselectionUtil.rememberSelection(aClass, firstClass);
        CommandProcessor.getInstance().executeCommand(project, () -> doInvoke(aClass, invokeImpl), getText(), null);
      })
      .setTitle(QuickFixBundle.message("target.class.chooser.title"));

    renderer.installSpeedSearch(builder);
    builder.createPopup().showInBestPositionFor(editor);
  }

  protected void setupVisibility(PsiClass parentClass, @NotNull PsiClass targetClass, PsiModifierList list) throws IncorrectOperationException {
    if (targetClass.isInterface() && list.getFirstChild() != null) {
      list.deleteChildRange(list.getFirstChild(), list.getLastChild());
      return;
    }
    if (targetClass.isInterface()) {
      return;
    }
    final String visibility = getVisibility(parentClass, targetClass);
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      list.setModifierProperty(PsiModifier.PRIVATE, true);
      VisibilityUtil.escalateVisibility(list, parentClass);
    } else {
      VisibilityUtil.setVisibility(list, visibility);
    }
  }

  @PsiModifier.ModifierConstant
  protected String getVisibility(PsiClass parentClass, @NotNull PsiClass targetClass) {
    if (parentClass != null && (parentClass.equals(targetClass) || PsiTreeUtil.isAncestor(targetClass, parentClass, true))) {
      return PsiModifier.PRIVATE;
    } else {
      return JavaCodeStyleSettings.getInstance(targetClass.getContainingFile()).VISIBILITY;
    }
  }

  public static boolean shouldCreateStaticMember(PsiReferenceExpression ref, PsiClass targetClass) {

    PsiExpression qualifierExpression = ref.getQualifierExpression();
    while (qualifierExpression instanceof PsiParenthesizedExpression) {
      qualifierExpression = ((PsiParenthesizedExpression) qualifierExpression).getExpression();
    }

    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      return referenceExpression.resolve() instanceof PsiClass;
    } else if (qualifierExpression != null) {
      return false;
    } else if (ref instanceof PsiMethodReferenceExpression) {
      return true;
    }
    else {
      assert PsiTreeUtil.isAncestor(targetClass, ref, true);
      PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(ref, PsiModifierListOwner.class);
      if (owner instanceof PsiMethod method && method.isConstructor()) {
        //usages inside delegating constructor call
        PsiExpression run = ref;
        while (run.getParent() instanceof PsiExpression) {
          run = (PsiExpression)run.getParent();
        }
        if (run.getParent() instanceof PsiExpressionList &&
          run.getParent().getParent() instanceof PsiMethodCallExpression) {
          @NonNls String calleeText = ((PsiMethodCallExpression)run.getParent().getParent()).getMethodExpression().getText();
          if (calleeText.equals(JavaKeywords.THIS) || calleeText.equals(JavaKeywords.SUPER)) return true;
        }
      }

      while (owner != null && owner != targetClass) {
        if (owner.hasModifierProperty(PsiModifier.STATIC)) return true;
        owner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner.class);
      }
    }

    return false;
  }

  private static @Nullable PsiExpression getQualifier (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement ref = ((PsiNewExpression) element).getClassReference();
      if (ref instanceof PsiReferenceExpression) {
        return ((PsiReferenceExpression) ref).getQualifierExpression();
      }
    } else if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression();
    }

    return null;
  }

  public static @NotNull PsiSubstitutor getTargetSubstitutor(@Nullable PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement reference = ((PsiNewExpression)element).getClassOrAnonymousClassReference();
      JavaResolveResult result = reference == null ? JavaResolveResult.EMPTY : reference.advancedResolve(false);
      return result.getSubstitutor();
    }

    PsiExpression qualifier = getQualifier(element);
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolveGenerics().getSubstitutor();
      }
    }

    return PsiSubstitutor.EMPTY;
  }

  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  //Should return only valid project classes
  protected @NotNull List<PsiClass> getTargetClasses(PsiElement element) {
    PsiClass psiClass = null;
    PsiExpression qualifier = null;

    if (element instanceof PsiNameValuePair) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
      if (annotation != null) {
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) {
          return Collections.emptyList();
        }
        else {
          final PsiElement resolve = nameRef.resolve();
          if (resolve instanceof PsiClass) {
            return Collections.singletonList((PsiClass)resolve);
          }
          else {
            return Collections.emptyList();
          }
        }
      }
    }
    if (element instanceof PsiNewExpression newExpression) {
      PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
      if (ref != null) {
        PsiElement refElement = ref.resolve();
        if (refElement instanceof PsiClass cls) {
          psiClass = cls;
        } else if (ref.getQualifier() instanceof PsiJavaCodeReferenceElement refQualifier) {
          refElement = refQualifier.resolve();
          if (refElement instanceof PsiClass cls) {
            psiClass = cls;
          }
        }
      }
      qualifier = newExpression.getQualifier();
    }
    else if (element instanceof PsiReferenceExpression) {
      qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
      if (qualifier == null && element instanceof PsiMethodReferenceExpression) {
        final PsiTypeElement qualifierTypeElement = ((PsiMethodReferenceExpression)element).getQualifierType();
        if (qualifierTypeElement != null) {
          psiClass = PsiUtil.resolveClassInType(qualifierTypeElement.getType());
        }
      } else if (qualifier == null) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiSwitchLabelStatement) {
          final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(parent, PsiSwitchStatement.class);
          if (switchStatement != null) {
            final PsiExpression expression = switchStatement.getExpression();
            if (expression != null) {
              psiClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
            }
          }
        }
      }
    }
    else if (element instanceof PsiMethodCallExpression) {
      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      qualifier = methodExpression.getQualifierExpression();
      final @NonNls String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) return Collections.emptyList();
    }
    boolean allowOuterClasses = false;
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        psiClass = ((PsiClassType)type).resolve();
      }

      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (resolved instanceof PsiClass) {
          if (psiClass == null) psiClass = (PsiClass)resolved;
        }
      }
    } else if (psiClass == null) {
      psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      allowOuterClasses = true;
    }

    if (psiClass instanceof PsiTypeParameter) {
      PsiClass[] supers = psiClass.getSupers();
      List<PsiClass> filtered = new ArrayList<>();
      for (PsiClass aSuper : supers) {
        if (!canModify(aSuper)) continue;
        if (!(aSuper instanceof PsiTypeParameter)) filtered.add(aSuper);
      }
      return filtered;
    }
    else {
      if (psiClass == null || !canModify(psiClass)) {
        return Collections.emptyList();
      }

      if (!allowOuterClasses || !isAllowOuterTargetClass()) {
        final ArrayList<PsiClass> classes = new ArrayList<>();
        collectSupers(psiClass, classes);
        return classes;
      }

      List<PsiClass> result = new ArrayList<>();

      while (psiClass != null) {
        result.add(psiClass);
        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) break;
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      }
      return result;
    }
  }

  private void collectSupers(PsiClass psiClass, ArrayList<? super PsiClass> classes) {
    classes.add(psiClass);

    final PsiClass[] supers = psiClass.getSupers();
    for (PsiClass aSuper : supers) {
      if (classes.contains(aSuper)) continue;
      if (canBeTargetClass(aSuper)) {
        collectSupers(aSuper, classes);
      }
    }
  }

  protected boolean canBeTargetClass(PsiClass psiClass) {
    return canModify(psiClass);
  }

  public static void startTemplate (@NotNull Editor editor, final Template template, final @NotNull Project project) {
    startTemplate(editor, template, project, null);
  }

  protected static void startTemplate(final @NotNull Editor editor,
                                      final Template template,
                                      final @NotNull Project project,
                                      final TemplateEditingListener listener) {
    startTemplate(editor, template, project, listener, null);
  }

  public static void startTemplate(final @NotNull Editor editor,
                                   final Template template,
                                   final @NotNull Project project,
                                   final TemplateEditingListener listener,
                                   final @NlsContexts.Command String commandName) {
    Runnable runnable = () -> TemplateManager.getInstance(project).startTemplate(editor, template, listener);
    if (!ApplicationManager.getApplication().isWriteIntentLockAcquired() || IntentionPreviewUtils.isIntentionPreviewActive()) {
      runnable.run();
    } else {
      CommandProcessor.getInstance().executeCommand(project, runnable, commandName, commandName);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void setupGenericParameters(PsiClass targetClass, PsiJavaCodeReferenceElement ref) {
    int numParams = ref.getTypeParameters().length;
    if (numParams == 0) return;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(ref.getProject());
    final Set<String> typeParamNames = new HashSet<>();
    for (PsiType type : ref.getTypeParameters()) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass instanceof PsiTypeParameter) {
        typeParamNames.add(psiClass.getName());
      }
    }
    int idx = 0;
    PsiTypeParameterList typeParameterList = Objects.requireNonNull(targetClass.getTypeParameterList());
    for (PsiType type : ref.getTypeParameters()) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass instanceof PsiTypeParameter) {
        typeParameterList.add(factory.createTypeParameterFromText(psiClass.getName(), null));
      } else {
        while (true) {
          final @NonNls String paramName = idx > 0 ? "T" + idx : "T";
          if (typeParamNames.add(paramName)) {
            typeParameterList.add(factory.createTypeParameterFromText(paramName, null));
            break;
          }
          idx++;
        }
      }
    }
  }

  public static void startTemplate(@NotNull Project project, @NotNull PsiClass aClass, @NotNull Template template, @Nls @NotNull String text) {
    aClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(aClass);
    template.setToReformat(true);

    final Editor editor = CodeInsightUtil.positionCursor(project, Objects.requireNonNull(aClass).getContainingFile(), aClass);
    if (editor == null) return;

    Segment textRange = aClass.getTextRange();
    editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
    startTemplate(editor, template, project, null, text);
  }
}

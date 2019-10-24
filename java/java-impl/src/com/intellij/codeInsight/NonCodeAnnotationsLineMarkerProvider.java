// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.AnnotateIntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.codeInsight.javadoc.AnnotationDocGenerator;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.codeInspection.dataFlow.EditContractIntention;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class NonCodeAnnotationsLineMarkerProvider extends LineMarkerProviderDescriptor {
  protected enum LineMarkerType { External, InferredNullability, InferredContract }

  private final Function<PsiElement, String> myTooltipProvider = nameIdentifier -> {
    PsiModifierListOwner owner = (PsiModifierListOwner)nameIdentifier.getParent();

    return XmlStringUtil.wrapInHtml(NonCodeAnnotationGenerator.getNonCodeHeader(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()) +
                                    " available. Full signature:<p>\n" + JavaDocInfoGenerator.generateSignature(owner));
  };

  private static LineMarkerType getAnnotationLineMarkerType(PsiModifierListOwner owner) {
    Collection<? extends AnnotationDocGenerator> nonCodeAnnotations =
      NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values();
    if (ContainerUtil.find(nonCodeAnnotations, (anno) -> !anno.isInferred()) != null) {
      return LineMarkerType.External;
    }

    if (ContainerUtil.find(nonCodeAnnotations, (anno) -> anno.isInferred() && !isContract(anno)) != null) {
      return LineMarkerType.InferredNullability;
    }

    if (!nonCodeAnnotations.isEmpty()) {
      return LineMarkerType.InferredContract;
    }

    return null;
  }

  private static boolean isContract(AnnotationDocGenerator anno) {
    return Contract.class.getName().equals(anno.getAnnotationQualifiedName());
  }

  private final String myName;
  private final LineMarkerType myLineMarkerType;

  protected NonCodeAnnotationsLineMarkerProvider(String name,
                                                 LineMarkerType lineMarkerType) {
    myName = name;
    myLineMarkerType = lineMarkerType;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    PsiModifierListOwner owner = getAnnotationOwner(element);
    if (owner == null) return null;

    if (!hasAnnotationsToShow(owner)) {
      return null;
    }

    return new LineMarkerInfo<>(element, element.getTextRange(),
                                AllIcons.Gutter.ExtAnnotation,
                                myTooltipProvider, MyIconGutterHandler.INSTANCE,
                                GutterIconRenderer.Alignment.RIGHT);
  }

  protected boolean hasAnnotationsToShow(@NotNull PsiModifierListOwner owner) {
    return getAnnotationLineMarkerType(owner) == myLineMarkerType;
  }

  @Nullable
  static PsiModifierListOwner getAnnotationOwner(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiElement owner = element.getParent();
    if (!(owner instanceof PsiModifierListOwner) || !(owner instanceof PsiNameIdentifierOwner)) return null;
    if (owner instanceof PsiParameter || owner instanceof PsiLocalVariable) return null;

    // support non-Java languages where getNameIdentifier may return non-physical psi with the same range
    PsiElement nameIdentifier = ((PsiNameIdentifierOwner)owner).getNameIdentifier();
    if (nameIdentifier == null || !nameIdentifier.getTextRange().equals(element.getTextRange())) return null;
    return (PsiModifierListOwner)owner;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Gutter.ExtAnnotation;
  }

  private static class MyIconGutterHandler implements GutterIconNavigationHandler<PsiElement> {
    static final MyIconGutterHandler INSTANCE = new MyIconGutterHandler();

    @Override
    public void navigate(MouseEvent e, PsiElement nameIdentifier) {
      final PsiElement listOwner = nameIdentifier.getParent();
      final PsiFile containingFile = listOwner.getContainingFile();
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(listOwner);

      if (virtualFile != null && containingFile != null) {
        final Project project = listOwner.getProject();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
          editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

          if (file != null && virtualFile.equals(file.getVirtualFile())) {
            final JBPopup popup = createActionGroupPopup(containingFile, project, editor);
            if (popup != null) {
              popup.show(new RelativePoint(e));
            }
          }
        }
      }
    }

    @Nullable
    private static JBPopup createActionGroupPopup(PsiFile file, Project project, Editor editor) {
      List<AnAction> actions = StreamEx.of(getMethodActions(file, project, editor),
                                           getParameterAnnotationActions(file, project, editor))
                                       .remove(List::isEmpty)
                                       .intersperse(Collections.singletonList(Separator.create()))
                                       .toFlatList(l -> l);

      if (!actions.isEmpty()) {
        final DefaultActionGroup group = new DefaultActionGroup(actions);
        final DataContext context = SimpleDataContext.getProjectContext(null);
        return JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      }

      return null;
    }

    @NotNull
    private static List<AnAction> getMethodActions(PsiFile file, Project project, Editor editor) {
      Comparator<IntentionAction> comparator =
        Comparator.comparing((IntentionAction action) ->
                               action instanceof PriorityAction ? ((PriorityAction)action).getPriority() : PriorityAction.Priority.NORMAL)
                  .thenComparing(IntentionAction::getText);
      return Stream.of(IntentionManager.getInstance().getAvailableIntentionActions())
                   .map(action -> IntentionActionDelegate.unwrap(action))
                   .filter(action -> shouldShowInGutterPopup(action) && action.isAvailable(project, editor, file))
                   .sorted(comparator)
                   .map(action -> new ApplyIntentionAction(action, action.getText(), editor, file))
                   .collect(Collectors.toList());
    }

    @NotNull
    private static List<AnAction> getParameterAnnotationActions(@NotNull PsiFile file, Project project, Editor editor) {
      final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
      if (leaf == null) return Collections.emptyList();
      PsiMethod method = ObjectUtils.tryCast(leaf.getParent(), PsiMethod.class);
      if (method == null) return Collections.emptyList();
      List<AnAction> actions = new ArrayList<>();
      for (PsiParameter parameter: method.getParameterList().getParameters()) {
        MakeInferredAnnotationExplicit intention = new MakeInferredAnnotationExplicit();
        if (intention.isAvailable(project, file, parameter)) {
          actions.add(new AnAction(intention.getText() + " on parameter '" + parameter.getName() + "'") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              intention.makeAnnotationsExplicit(project, file, parameter);
            }
          });
        }
      }
      return actions;
    }

    private static boolean shouldShowInGutterPopup(IntentionAction action) {
      return action instanceof AnnotateIntentionAction ||
             action instanceof DeannotateIntentionAction ||
             action instanceof EditContractIntention ||
             action instanceof MakeInferredAnnotationExplicit ||
             action instanceof MakeExternalAnnotationExplicit;
    }
  }
}

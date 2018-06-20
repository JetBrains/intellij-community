// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.AnnotateIntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.codeInspection.dataFlow.EditContractIntention;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ExternalAnnotationsLineMarkerProvider extends LineMarkerProviderDescriptor {
  private static final Function<PsiElement, String> ourTooltipProvider = nameIdentifier -> {
    PsiModifierListOwner owner = (PsiModifierListOwner)nameIdentifier.getParent();

    return XmlStringUtil.wrapInHtml(NonCodeAnnotationGenerator.getNonCodeHeader(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()) +
                                    " available. Full signature:<p>\n" + JavaDocInfoGenerator.generateSignature(owner));
  };

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    PsiModifierListOwner owner = getAnnotationOwner(element);
    if (owner == null) return null;

    boolean includeSourceInferred = CodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS;
    boolean hasAnnotationsToShow = ContainerUtil.exists(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values(),
                                                        a -> includeSourceInferred || !a.isInferredFromSource());
    if (!hasAnnotationsToShow) {
      return null;
    }

    return new LineMarkerInfo<>(element, element.getTextRange(),
                                AllIcons.Gutter.ExtAnnotation,
                                Pass.LINE_MARKERS,
                                ourTooltipProvider, MyIconGutterHandler.INSTANCE,
                                GutterIconRenderer.Alignment.RIGHT);
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

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {}

  @NotNull
  @Override
  public String getName() {
    return "External annotations";
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
      final DefaultActionGroup group = new DefaultActionGroup();
      Comparator<IntentionAction> comparator =
        Comparator.comparing((IntentionAction action) ->
                               action instanceof PriorityAction ? ((PriorityAction)action).getPriority() : PriorityAction.Priority.NORMAL)
                  .thenComparing(IntentionAction::getText);
      Stream.of(IntentionManager.getInstance().getAvailableIntentionActions())
            .map(action -> action instanceof IntentionActionDelegate ? ((IntentionActionDelegate)action).getDelegate() : action)
            .filter(action -> shouldShowInGutterPopup(action) && action.isAvailable(project, editor, file))
            .sorted(comparator)
            .map(action -> new ApplyIntentionAction(action, action.getText(), editor, file))
            .forEach(group::add);
      addParameterAnnotationActions(file, project, editor, group);

      if (group.getChildrenCount() > 0) {
        final DataContext context = SimpleDataContext.getProjectContext(null);
        return JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      }

      return null;
    }

    private static void addParameterAnnotationActions(PsiFile file, Project project, Editor editor, DefaultActionGroup group) {
      final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
      if (leaf == null) return;
      PsiMethod method = ObjectUtils.tryCast(leaf.getParent(), PsiMethod.class);
      if (method == null) return;
      boolean hasSeparator = false;
      for (PsiParameter parameter: method.getParameterList().getParameters()) {
        MakeInferredAnnotationExplicit intention = new MakeInferredAnnotationExplicit();
        if (intention.isAvailable(project, file, parameter)) {
          if (!hasSeparator) {
            hasSeparator = true;
            group.addSeparator();
          }
          group.add(new AnAction(intention.getText() + " on parameter '" + parameter.getName() + "'") {
            @Override
            public void actionPerformed(AnActionEvent e) {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              intention.makeAnnotationsExplicit(project, file, parameter);
            }
          });
        }
      }
    }

    private static boolean shouldShowInGutterPopup(IntentionAction action) {
      return action instanceof AnnotateIntentionAction ||
             action instanceof DeannotateIntentionAction ||
             action instanceof EditContractIntention ||
             action instanceof ToggleSourceInferredAnnotations ||
             action instanceof MakeInferredAnnotationExplicit ||
             action instanceof MakeExternalAnnotationExplicit;
    }
  }
}

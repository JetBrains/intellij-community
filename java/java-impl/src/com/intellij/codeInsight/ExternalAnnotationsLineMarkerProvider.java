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
package com.intellij.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class ExternalAnnotationsLineMarkerProvider implements LineMarkerProvider {
  private static final Function<PsiElement, String> ourTooltipProvider = new Function<PsiElement, String>() {
    @Override
    public String fun(PsiElement nameIdentifier) {
      return XmlStringUtil.wrapInHtml(JavaDocInfoGenerator.generateSignature(nameIdentifier.getParent()));
    }
  };

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    if (!(element instanceof PsiModifierListOwner) || !(element instanceof PsiNameIdentifierOwner)) return null;
    if (element instanceof PsiParameter || element instanceof PsiLocalVariable) return null;

    PsiElement nameIdentifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
    if (nameIdentifier == null || nameIdentifier.getParent() != element) return null;

    if (!shouldShowSignature((PsiModifierListOwner)element)) {
      return null;
    }

    return new LineMarkerInfo<PsiElement>(nameIdentifier, nameIdentifier.getTextRange().getStartOffset(),
                                          AllIcons.Gutter.ExtAnnotation,
                                          Pass.UPDATE_ALL,
                                          ourTooltipProvider, MyIconGutterHandler.INSTANCE,
                                          GutterIconRenderer.Alignment.LEFT);
  }

  private static boolean shouldShowSignature(PsiModifierListOwner owner) {
    if (hasNonCodeAnnotations(owner)) {
      return true;
    }

    if (owner instanceof PsiMethod) {
      for (PsiParameter parameter : ((PsiMethod)owner).getParameterList().getParameters()) {
        if (hasNonCodeAnnotations(parameter)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean hasNonCodeAnnotations(@NotNull PsiModifierListOwner element) {
    Project project = element.getProject();
    PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(element);
    if (externalAnnotations != null) {
      for (PsiAnnotation annotation : externalAnnotations) {
        if (isVisibleAnnotation(annotation)) {
          return true;
        }
      }
    }
    for (PsiAnnotation annotation : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(element)) {
      if (isVisibleAnnotation(annotation)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isVisibleAnnotation(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
    if (ref == null) return true;

    PsiElement target = ref.resolve();
    return !(target instanceof PsiClass) || JavaDocInfoGenerator.isDocumentedAnnotationType(target);
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {}

  private static class MyIconGutterHandler implements GutterIconNavigationHandler<PsiElement> {
    static final MyIconGutterHandler INSTANCE = new MyIconGutterHandler();

    @Override
    public void navigate(MouseEvent e, PsiElement nameIdentifier) {
      final PsiElement listOwner = nameIdentifier.getParent();
      final PsiFile containingFile = listOwner.getContainingFile();
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(listOwner);
      if (virtualFile != null && containingFile != null) {
        final Project project = listOwner.getProject();
        final OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project, virtualFile, listOwner.getTextOffset());
        final Editor editor = FileEditorManager.getInstance(project).openTextEditor(openFileDescriptor, true);
        if (editor != null) {
          final DefaultActionGroup group = new DefaultActionGroup();
          for (final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions()) {
            if (action.isAvailable(project, editor, containingFile)) {
              group.add(new ApplyIntentionAction(action, action.getText(), editor, containingFile));
            }
          }
          if (group.getChildrenCount() > 0) {
            editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
              @Override
              public void run() {
                JBPopupFactory.getInstance()
                  .createActionGroupPopup(null, group, SimpleDataContext.getProjectContext(null),
                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
                  .showInBestPositionFor(editor);
              }
            });
          }
        }
      }
    }
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ClickListener;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public final class FileLevelIntentionComponent extends EditorNotificationPanel {
  public FileLevelIntentionComponent(@NlsContexts.Label String description,
                                     @NotNull HighlightSeverity severity,
                                     @Nullable GutterMark gutterMark,
                                     @Nullable List<? extends Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> intentions,
                                     @NotNull PsiFile psiFile,
                                     @NotNull Editor editor, @NlsContexts.Tooltip @Nullable String tooltip) {
    super(getColor(psiFile.getProject(), severity), getStatus(psiFile.getProject(), severity));
    Project project = psiFile.getProject();
    ShowIntentionsPass.IntentionsInfo info = new ShowIntentionsPass.IntentionsInfo();

    if (intentions != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> intention : intentions) {
        HighlightInfo.IntentionActionDescriptor descriptor = intention.getFirst();
        IntentionAction action = descriptor.getAction();
        ReadAction.nonBlocking(() -> action.isAvailable(project, editor, psiFile))
          .expireWith(project)
          .inSmartMode(project)
          .finishOnUiThread(ModalityState.nonModal(), isAvailable -> {
            if (!isAvailable) return;
            info.intentionsToShow.add(descriptor);
            if (action instanceof EmptyIntentionAction) {
              return;
            }
            String text = action.getText();
            createActionLabel(text, () -> {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, text);
            });
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    }

    myLabel.setText(description);
    myLabel.setToolTipText(tooltip);
    if (gutterMark != null) {
      myLabel.setIcon(gutterMark.getIcon());
    }

    if (intentions != null && !intentions.isEmpty()) {
      IntentionAction intentionAction = intentions.get(0).getFirst().getAction();
      if (!(intentionAction instanceof UserDataHolder) ||
          !Boolean.FALSE.equals(((UserDataHolder)intentionAction).getUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY))) {
        // do not show gear icon if this intention action is explicitly marked with `SHOW_INTENTION_OPTIONS_KEY = false`

        myGearLabel.setIcon(AllIcons.General.GearPlain);

        SmartPsiElementPointer<PsiFile> filePointer = SmartPointerManager.createPointer(psiFile);
        new ClickListener() {
          @Override
          public boolean onClick(@NotNull MouseEvent e, int clickCount) {
            PsiFile psiFile = filePointer.getElement();
            if (psiFile == null) return true;
            CachedIntentions cachedIntentions = new CachedIntentions(project, psiFile, editor);
            IntentionListStep step = new IntentionListStep(null, editor, psiFile, project, cachedIntentions);
            HighlightInfo.IntentionActionDescriptor descriptor = intentions.get(0).getFirst();
            IntentionActionWithTextCaching actionWithTextCaching = cachedIntentions.wrapAction(descriptor, psiFile, psiFile, editor);
            if (step.hasSubstep(actionWithTextCaching)) {
              step = step.getSubStep(actionWithTextCaching, null);
            }
            ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
            Dimension dimension = popup.getContent().getPreferredSize();
            Point at = new Point(-dimension.width + myGearLabel.getWidth(), FileLevelIntentionComponent.this.getHeight());
            popup.show(new RelativePoint(e.getComponent(), at));
            return true;
          }
        }.installOn(myGearLabel);
      }
    }
  }

  private static @NotNull Color getColor(@NotNull Project project, @NotNull HighlightSeverity severity) {
    if (SeverityRegistrar.getSeverityRegistrar(project).compare(severity, HighlightSeverity.ERROR) >= 0) {
      return LightColors.RED;
    }

    if (SeverityRegistrar.getSeverityRegistrar(project).compare(severity, HighlightSeverity.WARNING) >= 0) {
      return LightColors.YELLOW;
    }

    return LightColors.GREEN;
  }

  private static @NotNull Status getStatus(@NotNull Project project, @NotNull HighlightSeverity severity) {
    if (SeverityRegistrar.getSeverityRegistrar(project).compare(severity, HighlightSeverity.ERROR) >= 0) {
      return Status.Error;
    }

    if (SeverityRegistrar.getSeverityRegistrar(project).compare(severity, HighlightSeverity.WARNING) >= 0) {
      return Status.Warning;
    }

    return Status.Success;
  }
}

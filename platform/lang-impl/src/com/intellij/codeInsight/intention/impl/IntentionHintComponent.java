package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.RowIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;

public abstract class IntentionHintComponent implements Disposable {
  private static final Icon ourInactiveArrowIcon = new EmptyIcon(AllIcons.General.ArrowDown.getIconWidth(), AllIcons.General.ArrowDown.getIconHeight());
  protected final Editor myEditor;
  protected final RowIcon myHighlightedIcon;
  protected final RowIcon myInactiveIcon;
  protected final Icon mySmartTagIcon;
  protected final PsiFile myFile;
  protected final IntentionListStep myIntentionListStep;
  protected boolean myDisposed;

  public IntentionHintComponent(Project project,
                                @NotNull PsiFile file,
                                @NotNull final Editor editor,
                                @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    myFile = file;
    myEditor = editor;
    boolean showRefactoringsBulb = ContainerUtil.exists(intentions.inspectionFixesToShow, new Condition<HighlightInfo.IntentionActionDescriptor>() {
        @Override
        public boolean value(HighlightInfo.IntentionActionDescriptor descriptor) {
          return descriptor.getAction() instanceof BaseRefactoringIntentionAction;
        }
      });
    boolean showFix = !showRefactoringsBulb && ContainerUtil.exists(intentions.errorFixesToShow, new Condition<HighlightInfo.IntentionActionDescriptor>() {
      @Override
      public boolean value(HighlightInfo.IntentionActionDescriptor descriptor) {
        return IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction());
      }
    });

    mySmartTagIcon = showRefactoringsBulb ? AllIcons.Actions.RefactoringBulb : showFix ? AllIcons.Actions.QuickfixBulb : AllIcons.Actions.IntentionBulb;

    myHighlightedIcon = new RowIcon(mySmartTagIcon, AllIcons.General.ArrowDown);
    myInactiveIcon = new RowIcon(mySmartTagIcon, ourInactiveArrowIcon);

    myIntentionListStep = new IntentionListStep(this, intentions, myEditor, myFile, project);
  }

  @NotNull
  public static IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                             @NotNull PsiFile file,
                                                             @NotNull Editor editor,
                                                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                             boolean showExpanded) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return IntentionHintComponentProvider.SERVICE.getInstance().showIntentionHint(project, file, editor, intentions, showExpanded);
  }

  public static void hideLastIntentionHint(@NotNull Editor editor) {
    IntentionHintComponentProvider.SERVICE.getInstance().hideLastIntentionHint(editor);
  }

  public static IntentionHintComponent getLastIntentionHint(@NotNull Editor editor) {
    return IntentionHintComponentProvider.SERVICE.getInstance().getLastIntentionHint(editor);
  }

  @NotNull
  public static IntentionHintComponent showIntentionHint(@NotNull final Project project,
                                                             @NotNull PsiFile file,
                                                             @NotNull final Editor editor,
                                                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                             boolean showExpanded,
                                                             @NotNull Point position) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return IntentionHintComponentProvider.SERVICE.getInstance().showIntentionHint(project, file, editor, intentions, showExpanded, position);
  }

  public abstract boolean isVisible();

  public abstract boolean isShown();

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isForEditor(@NotNull Editor editor) {
    return editor == myEditor;
  }

  //true if actions updated, there is nothing to do
  //false if has to recreate popup, no need to reshow
  //null if has to reshow
  @NotNull
  public PopupUpdateResult updateActions(@NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    if (isDisposed() || !myFile.isValid()) {
      return PopupUpdateResult.HIDE_AND_RECREATE;
    }

    if (!myIntentionListStep.wrapAndUpdateActions(intentions, true)) {
      return PopupUpdateResult.NOTHING_CHANGED;
    }
    if (!isShown()) {
      return PopupUpdateResult.CHANGED_INVISIBLE;
    }
    return PopupUpdateResult.HIDE_AND_RECREATE;
  }

  @Nullable
  @TestOnly
  public IntentionAction getAction(int index) {
    if (isDisposed()) {
      return null;
    }
    List<IntentionActionWithTextCaching> values = myIntentionListStep.getValues();
    if (values.size() <= index) {
      return null;
    }
    return values.get(index).getAction();
  }

  public abstract void recreate();

  public abstract void hide();

  @NotNull
  protected static ListPopup createPopup(Editor editor, PsiFile file, @NotNull ListPopupStep step) {
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    if (popup instanceof WizardPopup) {
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          if (keyboardShortcut.getSecondKeyStroke() == null) {
            ((WizardPopup)popup).registerAction("activateSelectedElement", keyboardShortcut.getFirstKeyStroke(), new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                popup.handleSelect(true);
              }
            });
          }
        }
      }
    }

    boolean committed = PsiDocumentManager.getInstance(file.getProject()).isCommitted(editor.getDocument());
    final PsiFile injectedFile = committed ? InjectedLanguageUtil.findInjectedPsiNoCommit(file, editor.getCaretModel().getOffset()) : null;
    final Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);

    final ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    final ScopeHighlighter injectionHighlighter = new ScopeHighlighter(injectedEditor);

    popup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        highlighter.dropHighlight();
        injectionHighlighter.dropHighlight();
      }
    });
    popup.addListSelectionListener(e -> {
      final Object source = e.getSource();
      highlighter.dropHighlight();
      injectionHighlighter.dropHighlight();

      if (source instanceof DataProvider) {
        final Object selectedItem = PlatformDataKeys.SELECTED_ITEM.getData((DataProvider)source);
        if (selectedItem instanceof IntentionActionWithTextCaching) {
          final IntentionAction action = ((IntentionActionWithTextCaching)selectedItem).getAction();
          if (action instanceof SuppressIntentionActionFromFix) {
            if (injectedFile != null && ((SuppressIntentionActionFromFix)action).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
              final PsiElement at = injectedFile.findElementAt(injectedEditor.getCaretModel().getOffset());
              final PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
              if (container != null) {
                injectionHighlighter.highlight(container, Collections.singletonList(container));
              }
            }
            else {
              final PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
              final PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
              if (container != null) {
                highlighter.highlight(container, Collections.singletonList(container));
              }
            }
          }
        }
      }
    });
    return popup;
  }

  public abstract void canceled(@NotNull ListPopupStep intentionListStep);

  public enum PopupUpdateResult {
    NOTHING_CHANGED,    // intentions did not change
    CHANGED_INVISIBLE,  // intentions changed but the popup has not been shown yet, so can recreate list silently
    HIDE_AND_RECREATE   // ahh, has to close already shown popup, recreate and re-show again
  }
}

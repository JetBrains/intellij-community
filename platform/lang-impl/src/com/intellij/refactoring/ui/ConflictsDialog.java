// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.ui;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ConflictsDialogBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

public class ConflictsDialog extends DialogWrapper implements ConflictsDialogBase {
  private static final int SHOW_CONFLICTS_EXIT_CODE = 4;
  private static final int MAX_CONFLICTS_SHOWN = 20;
  @NonNls private static final String EXPAND_LINK = "expand";

  protected final String[] myConflictDescriptions;
  protected MultiMap<PsiElement, String> myElementConflictDescription;
  private final Project myProject;
  private Runnable myDoRefactoringRunnable;
  private final boolean myCanShowConflictsInView;
  @NlsContexts.Command private String myCommandName;

  public ConflictsDialog(@NotNull Project project, @NotNull MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflictDescriptions) {
    this(project, conflictDescriptions, null, true, true);
  }

  public ConflictsDialog(@NotNull Project project,
                         @NotNull MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflictDescriptions,
                         @Nullable Runnable doRefactoringRunnable) {
    this(project, conflictDescriptions, doRefactoringRunnable, true, true);
  }

  public ConflictsDialog(@NotNull Project project,
                         @NotNull MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflictDescriptions,
                         @Nullable Runnable doRefactoringRunnable,
                         boolean alwaysShowOkButton,
                         boolean canShowConflictsInView) {
    super(project, true);
    myProject = project;
    myDoRefactoringRunnable = doRefactoringRunnable;
    myCanShowConflictsInView = canShowConflictsInView;

    final LinkedHashSet<String> conflicts = new LinkedHashSet<>(conflictDescriptions.values());
    myConflictDescriptions = ArrayUtilRt.toStringArray(conflicts);
    myElementConflictDescription = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    setOKActionEnabled(alwaysShowOkButton || getDoRefactoringRunnable(null) != null);
    init();
  }

  /**
   * @deprecated use other CTORs
   */
  @Deprecated(forRemoval = true)
  public ConflictsDialog(Project project, String... conflictDescriptions) {
    super(project, true);
    myProject = project;
    myConflictDescriptions = conflictDescriptions;
    myCanShowConflictsInView = true;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    init();
  }

  @Override
  protected Action @NotNull [] createActions(){
    final Action okAction = getOKAction();
    boolean showUsagesButton = myElementConflictDescription != null && myCanShowConflictsInView;

    if (showUsagesButton || !okAction.isEnabled()) {
      okAction.putValue(DEFAULT_ACTION, null);
    }

    if (!showUsagesButton) {
      return new Action[]{okAction,new CancelAction()};
    }
    return new Action[]{okAction, new MyShowConflictsInUsageViewAction(), new CancelAction()};
  }

  @Override
  public boolean isShowConflicts() {
    return getExitCode() == SHOW_CONFLICTS_EXIT_CODE;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 2));

    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);

    HtmlBuilder buf = new HtmlBuilder();

    for (int i = 0; i < Math.min(myConflictDescriptions.length, MAX_CONFLICTS_SHOWN); i++) {
      buf.appendRaw(myConflictDescriptions[i]).br().br();
    }

    if (myConflictDescriptions.length > MAX_CONFLICTS_SHOWN) {
      buf.appendLink(EXPAND_LINK, RefactoringBundle.message("show.more.conflicts.link"));
    }

    JEditorPane messagePane = new JEditorPane();
    messagePane.setEditorKit(HTMLEditorKitBuilder.simple());
    messagePane.setText(buf.toString());
    messagePane.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(messagePane,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setPreferredSize(JBUI.size(500, 400));
    messagePane.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && 
          EXPAND_LINK.equals(e.getDescription())) {
        messagePane.setText(StringUtil.join(myConflictDescriptions, "<br><br>"));
      }
    });
    panel.add(scrollPane, BorderLayout.CENTER);

    if (getOKAction().isEnabled()) {
      panel.add(new JLabel(RefactoringBundle.message("do.you.wish.to.ignore.them.and.continue")), BorderLayout.SOUTH);
    }

    return panel;
  }

  @Override
  public void setCommandName(@NlsContexts.Command String commandName) {
    myCommandName = commandName;
  }

  private class CancelAction extends AbstractAction {
    CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
      putValue(DEFAULT_ACTION,Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  protected Runnable getDoRefactoringRunnable(@Nullable UsageView usageView) {
    return myDoRefactoringRunnable;
  }

  private class MyShowConflictsInUsageViewAction extends AbstractAction {


    MyShowConflictsInUsageViewAction() {
      super(RefactoringBundle.message("action.show.conflicts.in.view.text"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final UsageViewPresentation presentation = new UsageViewPresentation();
      final String codeUsagesString = RefactoringBundle.message("conflicts.tab.name");
      presentation.setCodeUsagesString(codeUsagesString);
      presentation.setTabName(codeUsagesString);
      presentation.setTabText(codeUsagesString);
      presentation.setShowCancelButton(true);

      final ArrayList<Usage> usages = new ArrayList<>(myElementConflictDescription.values().size());
      for (final PsiElement element : myElementConflictDescription.keySet()) {
        if (element == null) {
          usages.add(new DescriptionOnlyUsage());
          continue;
        }
        ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(element);
        ReadWriteAccessDetector.Access access = detector != null ? detector.getExpressionAccess(element) : null;
        for (final @NlsContexts.Tooltip String conflictDescription : myElementConflictDescription.get(element)) {
          final UsagePresentation usagePresentation = new DescriptionOnlyUsage(conflictDescription).getPresentation();
          Usage usage = access != null ? new ReadWriteAccessUsageInfo2UsageAdapter(new UsageInfo(element), access) {
            @NotNull
            @Override
            public UsagePresentation getPresentation() {
              return usagePresentation;
            }
          } : new UsageInfo2UsageAdapter(new UsageInfo(element)) {
            @NotNull
            @Override
            public UsagePresentation getPresentation() {
              return usagePresentation;
            }
          };
          usages.add(usage);
        }
      }
      final UsageView usageView = UsageViewManager.getInstance(myProject)
        .showUsages(UsageTarget.EMPTY_ARRAY, usages.toArray(Usage.EMPTY_ARRAY), presentation);
      Runnable doRefactoringRunnable = getDoRefactoringRunnable(usageView);
      if (doRefactoringRunnable != null) {
        usageView.addPerformOperationAction(
          doRefactoringRunnable,
          myCommandName != null ? myCommandName : RefactoringBundle.message("retry.command"),
          LangBundle.message("conflicts.dialog.message.unable.to.perform.refactoring.changes.in.code.after.usages.have.been.found"),
          RefactoringBundle.message("usageView.doAction"));
      }
      close(SHOW_CONFLICTS_EXIT_CODE);
    }

    private class DescriptionOnlyUsage implements Usage {
      private final @NlsContexts.Tooltip String myConflictDescription;

      DescriptionOnlyUsage(@NotNull @NlsContexts.Tooltip String conflictDescription) {
        //noinspection HardCodedStringLiteral
        myConflictDescription = StringUtil.unescapeXmlEntities(conflictDescription)
          .replaceAll("<code>", "")
          .replaceAll("</code>", "")
          .replaceAll("<b>", "")
          .replaceAll("</b>", "");
      }

      DescriptionOnlyUsage() {
        myConflictDescription = getEscapedDescription(StringUtil.join(new LinkedHashSet<>(myElementConflictDescription.get(null)), "\n"));
      }

      @Contract(pure = true)
      private String getEscapedDescription(String conflictsMessage) {
        return Pattern.compile("<[^<>]*>").matcher(conflictsMessage).replaceAll("");
      }

      @Override
      @NotNull
      public UsagePresentation getPresentation() {
        return new UsagePresentation() {
          @Override
          public TextChunk @NotNull [] getText() {
            return new TextChunk[] {new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), myConflictDescription)};
          }

          @Override
          @Nullable
          public Icon getIcon() {
            return null;
          }

          @Override
          public String getTooltipText() {
            return myConflictDescription;
          }

          @Override
          @NotNull
          public String getPlainText() {
            return myConflictDescription;
          }
        };
      }

      @Override
      public boolean canNavigateToSource() {
        return false;
      }

      @Override
      public boolean canNavigate() {
        return false;
      }
      @Override
      public void navigate(boolean requestFocus) {}

      @Override
      public FileEditorLocation getLocation() {
        return null;
      }

      @Override
      public boolean isReadOnly() {
        return false;
      }

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public void selectInEditor() {}
      @Override
      public void highlightInEditor() {}
    }
  }
}
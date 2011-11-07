/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/**
 * created at Sep 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.ui;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

public class ConflictsDialog extends DialogWrapper{
  private static final int SHOW_CONFLICTS_EXIT_CODE = 4;

  private String[] myConflictDescriptions;
  private MultiMap<PsiElement, String> myElementConflictDescription;
  private final Project myProject;
  private Runnable myDoRefactoringRunnable;
  private String myCommandName;

  public ConflictsDialog(@NotNull Project project, @NotNull MultiMap<PsiElement, String> conflictDescriptions) {
    this(project, conflictDescriptions, null, true);
  }

  public ConflictsDialog(@NotNull Project project,
                         @NotNull MultiMap<PsiElement, String> conflictDescriptions,
                         @Nullable Runnable doRefactoringRunnable) {
    this(project, conflictDescriptions, doRefactoringRunnable, true);
  }

  public ConflictsDialog(@NotNull Project project,
                         @NotNull MultiMap<PsiElement, String> conflictDescriptions,
                         @Nullable Runnable doRefactoringRunnable,
                         boolean alwaysShowOkButton) {
    super(project, true);
    myProject = project;
    myDoRefactoringRunnable = doRefactoringRunnable;
    final LinkedHashSet<String> conflicts = new LinkedHashSet<String>();

    for (String conflict : conflictDescriptions.values()) {
      conflicts.add(conflict);
    }
    myConflictDescriptions = ArrayUtil.toStringArray(conflicts);
    myElementConflictDescription = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    setOKActionEnabled(alwaysShowOkButton || myDoRefactoringRunnable != null);
    init();
  }

  @SuppressWarnings("deprecation")
  @Deprecated
  public ConflictsDialog(Project project, Collection<String> conflictDescriptions) {
    this(project, ArrayUtil.toStringArray(conflictDescriptions));
  }

  @Deprecated
  public ConflictsDialog(Project project, String... conflictDescriptions) {
    super(project, true);
    myProject = project;
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    init();
  }

  protected Action[] createActions(){
    final Action okAction = getOKAction();
    if (myElementConflictDescription == null) {
      return new Action[]{okAction,new CancelAction()};
    }
    okAction.putValue(DEFAULT_ACTION, null);
    return new Action[]{okAction, new MyShowConflictsInUsageViewAction(), new CancelAction()};
  }

  public boolean isShowConflicts() {
    return getExitCode() == SHOW_CONFLICTS_EXIT_CODE;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 2));

    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);

    @NonNls String contentType = "text/html";
    @NonNls StringBuilder buf = new StringBuilder();
    for (String description : myConflictDescriptions) {
      buf.append(description);
      buf.append("<br><br>");
    }
    JEditorPane messagePane = new JEditorPane(contentType, buf.toString());
    messagePane.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(messagePane,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setPreferredSize(new Dimension(500, 400));
    panel.add(scrollPane, BorderLayout.CENTER);

    panel.add(new JLabel(RefactoringBundle.message("do.you.wish.to.ignore.them.and.continue")), BorderLayout.SOUTH);

    return panel;
  }

  public void setCommandName(String commandName) {
    myCommandName = commandName;
  }

  private class CancelAction extends AbstractAction {
    public CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
      putValue(DEFAULT_ACTION,Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  private class MyShowConflictsInUsageViewAction extends AbstractAction {


    public MyShowConflictsInUsageViewAction() {
      super("Show in view");
    }

    public void actionPerformed(ActionEvent e) {
      final UsageViewPresentation presentation = new UsageViewPresentation();
      final String codeUsagesString = "Conflicts";
      presentation.setCodeUsagesString(codeUsagesString);
      presentation.setTabName(codeUsagesString);
      presentation.setTabText(codeUsagesString);
      presentation.setShowCancelButton(true);

      final Usage[] usages = new Usage[myElementConflictDescription.size()];
      int i = 0;
      for (final PsiElement element : myElementConflictDescription.keySet()) {
        if (element == null) {
          usages[i++] = new DescriptionOnlyUsage();
          continue;
        }
        boolean isRead = false;
        boolean isWrite = false;
        for (ReadWriteAccessDetector detector : Extensions.getExtensions(ReadWriteAccessDetector.EP_NAME)) {
          if (detector.isReadWriteAccessible(element)) {
            final ReadWriteAccessDetector.Access access = detector.getExpressionAccess(element);
            isRead = access != ReadWriteAccessDetector.Access.Write;
            isWrite = access != ReadWriteAccessDetector.Access.Read;
            break;
          }
        }

        usages[i++] = isRead || isWrite ? new ReadWriteAccessUsageInfo2UsageAdapter(new UsageInfo(element), isRead, isWrite) {
          @NotNull
          @Override
          public UsagePresentation getPresentation() {
            final UsagePresentation usagePresentation = super.getPresentation();
            return MyShowConflictsInUsageViewAction.this.getPresentation(usagePresentation, element);
          }
        } : new UsageInfo2UsageAdapter(new UsageInfo(element)) {
          @NotNull
          @Override
          public UsagePresentation getPresentation() {
            final UsagePresentation usagePresentation = super.getPresentation();
            return MyShowConflictsInUsageViewAction.this.getPresentation(usagePresentation, element);
          }
        };
      }
      final UsageView usageView = UsageViewManager.getInstance(myProject).showUsages(UsageTarget.EMPTY_ARRAY, usages, presentation);
      if (myDoRefactoringRunnable != null) {
        usageView.addPerformOperationAction(
          myDoRefactoringRunnable,
          myCommandName != null ? myCommandName : RefactoringBundle.message("retry.command"), "Unable to perform refactoring. There were changes in code after the usages have been found.", RefactoringBundle.message("usageView.doAction"));
      }
      close(SHOW_CONFLICTS_EXIT_CODE);
    }

    private UsagePresentation getPresentation(final UsagePresentation usagePresentation, PsiElement element) {
      final Collection<String> elementConflicts = new LinkedHashSet<String>(myElementConflictDescription.get(element));
      final String conflictDescription = " (" + Pattern.compile("<[^<>]*>").matcher(StringUtil.join(elementConflicts, "\n")).replaceAll("") + ")";
      return new UsagePresentation() {
        @NotNull
        public TextChunk[] getText() {
          final TextChunk[] chunks = usagePresentation.getText();
          return ArrayUtil
            .append(chunks, new TextChunk(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.toTextAttributes(), conflictDescription));
        }

        @NotNull
        public String getPlainText() {
          return usagePresentation.getPlainText() + conflictDescription;
        }

        public Icon getIcon() {
          return usagePresentation.getIcon();
        }

        public String getTooltipText() {
          return usagePresentation.getTooltipText();
        }
      };
    }

    private class DescriptionOnlyUsage implements Usage {
      private final String myConflictDescription = Pattern.compile("<[^<>]*>").matcher(StringUtil.join(new LinkedHashSet<String>(myElementConflictDescription.get(null)), "\n")).replaceAll("");

      @NotNull
      public UsagePresentation getPresentation() {
        return new UsagePresentation() {
          @NotNull
          public TextChunk[] getText() {
            return new TextChunk[0];
          }

          @Nullable
          public Icon getIcon() {
            return null;
          }

          public String getTooltipText() {
            return myConflictDescription;
          }

          @NotNull
          public String getPlainText() {
            return myConflictDescription;
          }
        };
      }

      public boolean canNavigateToSource() {
        return false;
      }

      public boolean canNavigate() {
        return false;
      }
      public void navigate(boolean requestFocus) {}

      public FileEditorLocation getLocation() {
        return null;
      }

      public boolean isReadOnly() {
        return false;
      }

      public boolean isValid() {
        return true;
      }

      public void selectInEditor() {}
      public void highlightInEditor() {}
    }
  }
}

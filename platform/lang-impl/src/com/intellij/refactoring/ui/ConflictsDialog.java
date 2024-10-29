// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ConflictsDialogBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ConflictsDialog extends DialogWrapper implements ConflictsDialogBase {
  private static final int SHOW_CONFLICTS_EXIT_CODE = 4;
  private static final int MAX_CONFLICTS_SHOWN = 20;
  private static final @NonNls String EXPAND_LINK = "expand";

  protected final String[] myConflictDescriptions;
  protected final MultiMap<PsiElement, String> myElementConflictDescription;
  private final Project myProject;
  private final Runnable myDoRefactoringRunnable;
  private final boolean myCanShowConflictsInView;
  private @NlsContexts.Command String myCommandName;
  private JTree myTree;
  private final boolean myUpdatedDialog;

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

    LinkedHashSet<String> conflicts = new LinkedHashSet<>(conflictDescriptions.values());
    myConflictDescriptions = ArrayUtilRt.toStringArray(conflicts);
    myElementConflictDescription = conflictDescriptions;
    myUpdatedDialog = Registry.is("refactorings.use.updated.conflicts.detected.dialog");
    setTitle(myUpdatedDialog
             ? RefactoringBundle.message("conflicts.detected.title")
             : RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(myUpdatedDialog 
                    ? RefactoringBundle.message("refactor.anyway.button")
                    : RefactoringBundle.message("continue.button"));
    setOKActionEnabled(alwaysShowOkButton || getDoRefactoringRunnable(null) != null);
    init();
  }

  public List<String> getConflictDescriptions() {
    return List.of(myConflictDescriptions);
  }

  @Override
  public boolean showAndGet() {
    if (UiInterceptors.tryIntercept(this)) {
      disposeIfNeeded();
      return true;
    }
    return super.showAndGet();
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (myUpdatedDialog) return super.createActions();
    Action okAction = getOKAction();
    boolean showUsagesButton = myElementConflictDescription != null && myCanShowConflictsInView;

    if (showUsagesButton || !okAction.isEnabled()) {
      okAction.putValue(DEFAULT_ACTION, null);
    }

    if (!showUsagesButton) {
      return new Action[]{okAction,new CancelAction()};
    }
    return new Action[]{okAction, new ShowConflictsInUsageViewAction(), new CancelAction()};
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    if (myElementConflictDescription == null || !myCanShowConflictsInView || !myUpdatedDialog) return super.createLeftSideActions();
    return new Action[] {
      new ShowConflictsInUsageViewAction()
    };
  }

  @Override
  public boolean isShowConflicts() {
    return getExitCode() == SHOW_CONFLICTS_EXIT_CODE;
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return myUpdatedDialog ? DialogStyle.COMPACT : DialogStyle.NO_STYLE;
  }

    @Override
  protected JComponent createCenterPanel() {
    JPanel panel;
    
    if (myUpdatedDialog && myElementConflictDescription != null) {
      panel = new JPanel(new BorderLayout());
      UsageViewPresentation presentation = new UsageViewPresentation();
      presentation.setMergeDupLinesAvailable(false);
      presentation.setExcludeAvailable(false);
      presentation.setNonCodeUsageAvailable(false);
      UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(myProject)
        .createUsageView(UsageTarget.EMPTY_ARRAY, createUsages(), presentation, null);
      Disposer.register(getDisposable(), usageView);
      myTree = usageView.getTree();
      myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      for (MouseListener listener : myTree.getMouseListeners()) {
        if (listener instanceof PopupHandler) {
          myTree.removeMouseListener(listener);
          break;
        }
      }

      SimpleColoredComponent previewTitle = new SimpleColoredComponent();
      PopupUtil.applyPreviewTitleInsets(previewTitle);
      UsagePreviewPanel usagePreviewPanel = new UsagePreviewPanel(myProject, presentation);
      Disposer.register(getDisposable(), usagePreviewPanel);
      usagePreviewPanel.setShowTooltipBalloon(true);
      myTree.addTreeSelectionListener(e -> previewNode(myProject, e.getNewLeadSelectionPath(), usagePreviewPanel, previewTitle));
      
      JPanel previewPanel = new JPanel(new BorderLayout());
      if (ExperimentalUI.isNewUI()) previewPanel.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
      previewPanel.add(previewTitle, BorderLayout.NORTH);
      previewPanel.add(usagePreviewPanel, BorderLayout.CENTER);

      class MySplitter extends OnePixelSplitter implements UiDataProvider {

        MySplitter() {
          super(true, "conflicts.dialog.splitter", 0.4f);
        }

        @Override
        public void uiDataSnapshot(@NotNull DataSink sink) {
          sink.set(UsageView.USAGE_VIEW_SETTINGS_KEY, usageView.getUsageViewSettings());
        }
      }
      Splitter splitter = new MySplitter();
      splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, true));
      splitter.setSecondComponent(previewPanel);

      CommonActionsManager actionsManager = CommonActionsManager.getInstance();
      JPanel toolbarPanel = new JPanel(new BorderLayout());
      Border line = new CustomLineBorder(OnePixelDivider.BACKGROUND, 0, 0, 1, 0);
      toolbarPanel.setBorder(line);
      DefaultActionGroup groupBy = new DefaultActionGroup(createGroupingActions(usageView, splitter));
      groupBy.setPopup(true);
      groupBy.getTemplatePresentation().setIcon(AllIcons.Actions.GroupBy);
      groupBy.getTemplatePresentation().setText(UsageViewBundle.messagePointer("action.group.by.title"));
      ConflictOccurenceNavigatorSupport occurenceNavigator = new ConflictOccurenceNavigatorSupport(myTree);
      AnAction prevOccurenceAction = actionsManager.createPrevOccurenceAction(occurenceNavigator);
      prevOccurenceAction.registerCustomShortcutSet(myTree, getDisposable());
      AnAction nextOccurenceAction = actionsManager.createNextOccurenceAction(occurenceNavigator);
      nextOccurenceAction.registerCustomShortcutSet(myTree, getDisposable());
      DefaultActionGroup toolbarGroup = new DefaultActionGroup(
        prevOccurenceAction,
        nextOccurenceAction,
        new Separator(),
        groupBy,
        actionsManager.createExpandAllHeaderAction(myTree),
        actionsManager.createCollapseAllHeaderAction(myTree)
      );
      ActionManager actionManager = ActionManager.getInstance();
      ActionToolbarImpl toolbar = (ActionToolbarImpl)actionManager.createActionToolbar("ConflictsDialog", toolbarGroup, true);
      toolbar.setTargetComponent(myTree);

      toolbarPanel.add(toolbar.getComponent(), BorderLayout.WEST);
      JLabel conflictsLabel = new JLabel(RefactoringBundle.message("conflicts.count.label", myElementConflictDescription.values().size()));
      conflictsLabel.setBorder(new JBEmptyBorder(UIUtil.PANEL_REGULAR_INSETS));
      toolbarPanel.add(conflictsLabel, BorderLayout.EAST);
      SwingUtilities.invokeLater(() -> previewNode(myProject, myTree.getSelectionPath(), usagePreviewPanel, previewTitle));
      
      panel.add(splitter, BorderLayout.CENTER);
      panel.add(toolbarPanel, BorderLayout.NORTH);
    }
    else {
      panel = new JPanel(new BorderLayout(0, 2));
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
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && EXPAND_LINK.equals(e.getDescription())) {
          messagePane.setText(StringUtil.join(myConflictDescriptions, "<br><br>"));
        }
      });
      panel.add(scrollPane, BorderLayout.CENTER);

      if (getOKAction().isEnabled()) {
        panel.add(new JLabel(RefactoringBundle.message("do.you.wish.to.ignore.them.and.continue")), BorderLayout.SOUTH);
      }
    }
    return panel;
  }

  private List<AnAction> createGroupingActions(UsageView usageView, JComponent component) {
    List<AnAction> list = new ArrayList<>();
    ActionManager actionManager = ActionManager.getInstance();
    AnAction groupByUsageTypeAction = actionManager.getAction("UsageGrouping.UsageType");
    for (UsageGroupingRuleProvider provider : UsageGroupingRuleProvider.EP_NAME.getExtensionList()) {
      for (@NotNull AnAction action : provider.createGroupingActions(usageView)) {
        if (action != groupByUsageTypeAction) {
          action.registerCustomShortcutSet(component, getDisposable());
          list.add(action);
        }
      }
    }
    AnAction groupByModuleAction = ActionManager.getInstance().getAction("UsageGrouping.Module");
    AnAction flattenModulesAction = actionManager.getAction("UsageGrouping.FlattenModules");
    list.sort((o1, o2) -> {
      if (o1 == groupByModuleAction && o2 == flattenModulesAction) {
        return -1;
      }
      else if (o1 == flattenModulesAction && o2 == groupByModuleAction) {
        return 1;
      }
      return Comparing.compare(o1.getTemplateText(), o2.getTemplateText());
    });
    return list;
  }

  private static @NotNull UsageViewPresentation createPresentation() {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String codeUsagesString = RefactoringBundle.message("conflicts.tab.name");
    presentation.setCodeUsagesString(codeUsagesString);
    presentation.setTabName(codeUsagesString);
    presentation.setTabText(codeUsagesString);
    return presentation;
  }

  private Usage @NotNull [] createUsages() {
    ArrayList<Usage> usages = new ArrayList<>(myElementConflictDescription.values().size());
    for (PsiElement element : myElementConflictDescription.keySet()) {
      assert element != null;
      for (@NlsContexts.Tooltip String conflictDescription : myElementConflictDescription.get(element)) {
        UsagePresentation usagePresentation = new ConflictPresentation(conflictDescription);
        UsageInfo usageInfo = new UsageInfo(element) {
          @Override
          public @NlsSafe String getTooltipText() {
            return myUpdatedDialog ? "<html><body style='width: 300px'>" + usagePresentation.getPlainText() + "</body></html>" : null;
          }
        };
        Usage usage = new UsageInfo2UsageAdapter(usageInfo) {
          @Override
          public @NotNull UsagePresentation getPresentation() {
            return usagePresentation;
          }
        };
        usages.add(usage);
      }
    }
    return usages.toArray(Usage.EMPTY_ARRAY);
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myUpdatedDialog ? myTree : null;
  }

  private static void previewNode(Project project, TreePath path, UsagePreviewPanel usagePreviewPanel, SimpleColoredComponent previewTitle) {
    if (path == null) return;
    Object node = path.getLastPathComponent();
    Object userObject = TreeUtil.getUserObject(node);
    if (userObject instanceof UsageInfo2UsageAdapter adapter) {
      VirtualFile vFile = adapter.getFile();
      if (vFile != null) {
        previewTitle.clear();
        previewTitle.append(vFile.getPresentableName(), REGULAR_ATTRIBUTES);
        previewTitle.append(spaceAndThinSpace() + getPresentablePath(project, vFile),
                            new SimpleTextAttributes(STYLE_PLAIN, UIUtil.getContextHelpForeground()));
      }
      usagePreviewPanel.updateLayout(project, List.of(adapter.getUsageInfo()));
    }
  }

  private static @NlsSafe String getPresentablePath(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    String path;
    if (ScratchUtil.isScratch(virtualFile)) {
      path = ScratchUtil.getRelativePath(project, virtualFile);
    }
    else {
      VirtualFile baseDir = project.getBaseDir();
      path = VfsUtilCore.isAncestor(baseDir, virtualFile, true)
             ? VfsUtilCore.getRelativeLocation(virtualFile, baseDir)
             : FileUtil.getLocationRelativeToUserHome(virtualFile.getPath());
    }
    return path == null ? null : StringUtil.trimMiddle(path, 120);
  }

  @Override
  public void setCommandName(@NlsContexts.Command String commandName) {
    myCommandName = commandName;
  }

  private final class CancelAction extends AbstractAction {
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

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return myUpdatedDialog ? "conflicts.dialog" : null;
  }

  @Override
  public @Nullable Dimension getInitialSize() {
    return new Dimension(800, 600);
  }

  @Override
  public Dimension getPreferredSize() {
    return getInitialSize();
  }

  private final class ShowConflictsInUsageViewAction extends AbstractAction {

    ShowConflictsInUsageViewAction() {
      super(myUpdatedDialog 
            ? RefactoringBundle.message("action.open.in.find.window")
            : RefactoringBundle.message("action.show.conflicts.in.view.text"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      UsageViewPresentation presentation = createPresentation();
      presentation.setShowCancelButton(true);

      UsageView usageView =
        UsageViewManager.getInstance(myProject).showUsages(UsageTarget.EMPTY_ARRAY, createUsages(), presentation);
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
  }
  
  private static final class ConflictPresentation implements UsagePresentation {
    private static final String CODE_START = " <b>";
    private static final String CODE_END = "</b>";
    private final @NlsContexts.Tooltip String myConflictDescription;
    private final boolean myUpdatedDialog;

    ConflictPresentation(@NotNull @NlsContexts.Tooltip String conflictDescription) {
      myUpdatedDialog = Registry.is("refactorings.use.updated.conflicts.detected.dialog");
      if (myUpdatedDialog) {
        myConflictDescription = conflictDescription;
      }
      else {
        myConflictDescription = StringUtil.unescapeXmlEntities(conflictDescription)
          .replace("<code>", "")
          .replace("</code>", "")
          .replace("<b>", "")
          .replace("</b>", "");
      }
    }

    @Override
    public TextChunk @NotNull [] getText() {
      if (myUpdatedDialog) {
        List<TextChunk> chunks = new SmartList<>();
        int start = 0;
        String conflictDescription = StringUtil.unescapeXmlEntities(myConflictDescription)
          .replace("<b><code>", "<b>")
          .replace("<code>", "<b>")
          .replace("</code></b>", "</b>")
          .replace("</code>", "</b>");
        int refStart = conflictDescription.indexOf(CODE_START);
        while (refStart > 0) {
          // workaround for UsageViewTreeCellRenderer adding a space after the first chunk
          String substring = conflictDescription.substring(start, chunks.isEmpty() ? refStart : refStart + 1);
          chunks.add(new TextChunk(REGULAR_ATTRIBUTES.toTextAttributes(), substring));
          int refEnd = conflictDescription.indexOf(CODE_END, refStart);
          if (refEnd < 0) {
            return new TextChunk[]{new TextChunk(REGULAR_ATTRIBUTES.toTextAttributes(), conflictDescription)};
          }
          chunks.add(new TextChunk(REGULAR_BOLD_ATTRIBUTES.toTextAttributes(),
                                   conflictDescription.substring(refStart + CODE_START.length(), refEnd)));
          start = refEnd + CODE_END.length();
          refStart = conflictDescription.indexOf(CODE_START, refEnd);
        }
        chunks.add(new TextChunk(REGULAR_ATTRIBUTES.toTextAttributes(), conflictDescription.substring(start)));
        return chunks.toArray(TextChunk.EMPTY_ARRAY);
      }
      else {
        return new TextChunk[] {new TextChunk(REGULAR_ATTRIBUTES.toTextAttributes(), myConflictDescription)};
      }
    }

    @Override
    public @Nullable Icon getIcon() {
      return null;
    }

    @Override
    public String getTooltipText() {
      return myUpdatedDialog ? null : myConflictDescription;
    }

    @Override
    public @NotNull String getPlainText() {
      return myConflictDescription;
    }
  }
  
  private static class ConflictOccurenceNavigatorSupport extends OccurenceNavigatorSupport {

    public static final Navigatable DUMMY = new Navigatable() {};

    private ConflictOccurenceNavigatorSupport(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    public @NotNull String getNextOccurenceActionName() {
      return RefactoringBundle.message("action.next.conflict");
    }

    @Override
    public @NotNull String getPreviousOccurenceActionName() {
      return RefactoringBundle.message("action.previous.conflict");
    }

    @Override
    protected @Nullable Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
      return node.isLeaf() ? DUMMY : null;
    }
  }
}
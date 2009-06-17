package com.intellij.find.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.AbstractFindUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ShowUsagesAction extends AnAction {
  private final boolean showSettingsDialogBefore;
  private static final Function<Object,String> SPEED_SEARCH_TEXT = new Function<Object, String>() {
    public String fun(Object element) {
      if (!(element instanceof UsageNode)) return element.toString();
      StringBuilder text = new StringBuilder();
      UsageNode node = (UsageNode)element;
      Usage usage = node.getUsage();
      VirtualFile virtualFile = UsageListCellRenderer.getVirtualFile(usage);
      if (virtualFile != null) {
        text.append(virtualFile.getName());
      }
      TextChunk[] chunks = usage.getPresentation().getText();
      for (TextChunk chunk : chunks) {
        text.append(chunk.getText());
      }
      return text.toString();
    }
  };

  public ShowUsagesAction() {
    setInjectedContext(true);
    showSettingsDialogBefore = false;
  }

  public static class ShowSettings extends ShowUsagesAction {
    public ShowSettings() {
      super(true);
    }
  }

  private ShowUsagesAction(boolean showDialogBefore) {
    setInjectedContext(true);
    showSettingsDialogBefore = showDialogBefore;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
    final RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (usageTargets == null) {
      FindUsagesAction.chooseAmbiguousTargetAndPerform(project, editor, new PsiElementProcessor<PsiElement>() {
        public boolean execute(final PsiElement element) {
          startFindUsages(element, project, popupPosition, editor);
          return false;
        }
      });
    }
    else {
      PsiElement element = ((PsiElementUsageTarget)usageTargets[0]).getElement();
      startFindUsages(element, project, popupPosition, editor);
    }
  }

  private void startFindUsages(PsiElement element, Project project, RelativePoint popupPosition, Editor editor) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    if (showSettingsDialogBefore) {
      showDialogAndFindUsages(handler, project, element, popupPosition, null);
      return;
    }
    showElementUsages(handler, project, element, editor, popupPosition);
  }

  private void showElementUsages(FindUsagesHandler handler, @NotNull final Project project, final PsiElement element, final Editor editor,
                                 final RelativePoint popupPosition) {
    List<Usage> usages = new ArrayList<Usage>();
    Processor<Usage> collect = CommonProcessors.notNullProcessor(new CommonProcessors.CollectProcessor<Usage>(usages));
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    UsageViewPresentation presentation = findUsagesManager.processUsages(element, collect, handler);
    if (presentation == null) return;
    final String title = presentation.getTabText();
    JBPopup popup = createUsagePopup(usages, title, project, element, handler, editor, popupPosition);
    if (popup != null) {
      popup.show(popupPosition);
    }
  }

  private void showHint(final Project project, String text, final PsiElement element, final Editor editor, final RelativePoint popupPosition,
                               FindUsagesHandler handler) {
    JComponent label = createHintComponent(text, handler, project, element, popupPosition, editor, new Runnable() {
      public void run() {
        HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
      }
    });
    if (editor == null) {
      HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY |
                                                               HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
    }
    else {
      HintManager.getInstance().showInformationHint(editor, label);
    }
  }

  private JComponent createHintComponent(String text, final FindUsagesHandler handler, final Project project, final PsiElement element,
                                         final RelativePoint popupPosition, final Editor editor, final Runnable cancelAction) {
    JLabel label = HintUtil.createInformationLabel(text);
    InplaceButton button = createSettingsButton(handler, project, element, popupPosition, editor, cancelAction);
    JPanel panel = new JPanel(new BorderLayout());
    button.setBackground(label.getBackground());
    panel.setBackground(label.getBackground());
    label.setOpaque(false);
    label.setBorder(null);
    panel.setBorder(HintUtil.createHintBorder());
    panel.add(label, BorderLayout.CENTER);
    panel.add(button, BorderLayout.EAST);
    return panel;
  }

  private InplaceButton createSettingsButton(final FindUsagesHandler handler, final Project project, final PsiElement element, final RelativePoint popupPosition,
                                             final Editor editor,
                                             final Runnable cancelAction) {
    String shortcutText = "";
    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return new InplaceButton("Configure..." + shortcutText, IconLoader.getIcon("/general/ideOptions.png"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showDialogAndFindUsages(handler, project, element, popupPosition, editor);
          }
        });
        cancelAction.run();
      }
    });
  }

  private void showDialogAndFindUsages(FindUsagesHandler handler, Project project, PsiElement element, RelativePoint popupPosition,
                                       Editor editor) {
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(false, false, false);
    dialog.show();
    if (dialog.isOK()) {
      dialog.calcFindUsagesOptions();
      showElementUsages(handler, project, element, editor, popupPosition);
    }
  }

  private static String searchScopePresentableName(PsiElement element, final FindUsagesHandler handler) {
    SearchScope searchScope = FindUsagesManager.getCurrentSearchScope(handler);
    if (searchScope == null) searchScope = ProjectScope.getAllScope(element.getProject());
    return searchScope.getDisplayName();
  }

  private JBPopup createUsagePopup(final List<Usage> usages, final String title, final Project project, final PsiElement element, final FindUsagesHandler handler,
                                       final Editor editor,
                                       final RelativePoint popupPosition) {
    Usage[] arr = usages.toArray(new Usage[usages.size()]);
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    final UsageViewSettings save = new UsageViewSettings();

    save.loadState(usageViewSettings);
    usageViewSettings.GROUP_BY_FILE_STRUCTURE = false;
    usageViewSettings.GROUP_BY_MODULE = false;
    usageViewSettings.GROUP_BY_PACKAGE = false;
    usageViewSettings.GROUP_BY_USAGE_TYPE = false;

    final UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(project).createUsageView(UsageTarget.EMPTY_ARRAY, arr, presentation, null);
    Disposer.register(usageView, new Disposable() {
      public void dispose() {
        usageViewSettings.GROUP_BY_FILE_STRUCTURE = save.GROUP_BY_FILE_STRUCTURE;
        usageViewSettings.GROUP_BY_MODULE = save.GROUP_BY_MODULE;
        usageViewSettings.GROUP_BY_PACKAGE = save.GROUP_BY_PACKAGE;
        usageViewSettings.GROUP_BY_USAGE_TYPE = save.GROUP_BY_USAGE_TYPE;
      }
    });

    final GroupNode root = usageView.getRoot();
    final List<UsageNode> nodes = new ArrayList<UsageNode>();
    final Set<Usage> filteredUsages = new THashSet<Usage>();

    addUsageNodes(root, nodes, usageView, filteredUsages);
    if (nodes.isEmpty()) {
      if (usages.isEmpty()) {
        String text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(element, handler));
        showHint(project, text, element, editor, popupPosition, handler);
        Disposer.dispose(usageView);
        return null;
      }
      else {
        // all usages filtered out
      }
    }
    if (nodes.size() == 1 && usages.size() == 1) {
      //the only usage
      Usage usage = nodes.get(0).getUsage();
      navigateAndHint(usage, UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(element, handler)), project, element, handler,
                      popupPosition);
      Disposer.dispose(usageView);
      return null;
    }
    if (nodes.size() == 1 && usages.size() >= 1) {
      // usage view can filter usages down to one
      Usage usage = nodes.get(0).getUsage();
      navigateAndHint(usage, UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScopePresentableName(element, handler)),
                      project, element, handler, popupPosition);
      Disposer.dispose(usageView);
      return null;
    }

    Vector<Object> data = createListModel(nodes, usages);
    final JList list = new JList(data);
    list.setCellRenderer(new ShowUsagesListCellRenderer(usageView));

    final Runnable navigateRunnable = new Runnable() {
      public void run() {
        Object value = list.getSelectedValue();
        if (!(value instanceof UsageNode)) return;
        UsageNode node = (UsageNode)value;
        Usage usage = node.getUsage();
        navigateAndHint(usage, null, project, element, handler, popupPosition);
      }
    };

    ListSpeedSearch speedSearch = new ListSpeedSearch(list, SPEED_SEARCH_TEXT);
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator(false));

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title + " " +UsageViewBundle.message("some.usages.found", usages.size()));
    }

    builder.setMovable(true).setResizable(true);
    builder.setItemChoosenCallback(navigateRunnable);
    final JBPopup[] popup = new JBPopup[1];
    ActionListener editSettings = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        popup[0].cancel();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showDialogAndFindUsages(handler, project, element, popupPosition, editor);
          }
        });
      }
    };

    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      builder.registerKeyboardAction(shortcut.getFirstKeyStroke(), editSettings);
    }

    InplaceButton button = createSettingsButton(handler, project, element, popupPosition, editor, new Runnable() {
      public void run() {
        popup[0].cancel();
      }
    });
    builder.setCommandButton(button);


    DefaultActionGroup filters = new DefaultActionGroup();
    usageView.addFilteringActions(filters);
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, filters, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);
    builder.setSettingButton(toolBar);
  
    popup[0] = builder.createPopup();
    Disposer.register(popup[0], usageView);
    for (AnAction action : filters.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), popup[0].getContent());
    }


    final MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            nodes.clear();
            filteredUsages.clear();
            addUsageNodes(root, nodes, usageView, filteredUsages);

            Vector<Object> data = createListModel(nodes, usages);
            Dimension oldPreferred = list.getPreferredSize();
            list.setListData(data);
            list.setVisibleRowCount(data.size());
            Dimension newPreferred = list.getPreferredSize();

            JComponent content = popup[0].getContent();
            Window window = SwingUtilities.windowForComponent(content);

            Dimension d = new Dimension(window.getSize());
            d.setSize(d.width + newPreferred.width - oldPreferred.width, d.height + newPreferred.height - oldPreferred.height);
            window.setSize(d);
            window.validate();
            window.repaint();               
          }
        });
      }
    });

    return popup[0];
  }

  private static Vector<Object> createListModel(List<UsageNode> nodes, List<Usage> usages) {
    return nodes.isEmpty() ? new Vector<Object>(Arrays.asList(UsageViewBundle.message("usages.were.filtered.out", usages.size()))) : new Vector<Object>(nodes);
  }

  private static KeyboardShortcut getSettingsShortcut() {
    AnAction action = ActionManager.getInstance().getAction("ShowUsagesSettings");
    final ShortcutSet shortcutSet = action.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (final Shortcut shortcut : shortcuts) {
      KeyboardShortcut kb = (KeyboardShortcut)shortcut;
      if (kb.getSecondKeyStroke() == null) {
        return (KeyboardShortcut)shortcut;
      }
    }

    return null;
  }


  private static void addUsageNodes(GroupNode root, List<UsageNode> outNodes, final UsageViewImpl usageView, final Set<Usage> filteredUsages) {
    for (UsageNode node : root.getUsageNodes()) {
      Usage usage = node.getUsage();
      if (usageView.isVisible(usage)) {
        node.setParent(root);
        outNodes.add(node);
        filteredUsages.add(usage);
      }
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, outNodes, usageView, filteredUsages);
    }
  }

  public void update(AnActionEvent e){
    FindUsagesInFileAction.updateFindUsagesAction(e);
  }

  private void navigateAndHint(Usage usage, final String hint, final Project project, final PsiElement element, final FindUsagesHandler handler,
                                      final RelativePoint popupPosition) {
    usage.navigate(true);
    if (hint == null) return;
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    final Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
    if (newEditor != null) {
      //opening editor is performing in invokeLater
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
            public void run() {
              // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
                public void run() {
                  showHint(project, hint, element, newEditor, popupPosition, handler);
                }
              });
            }
          });
        }
      });
    }
  }
}
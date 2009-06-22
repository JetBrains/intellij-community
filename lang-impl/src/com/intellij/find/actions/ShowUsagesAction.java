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
import com.intellij.usages.impl.NullUsage;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
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

  private static final int USAGES_PAGE_SIZE = 100;

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
          startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
          return false;
        }
      });
    }
    else {
      PsiElement element = ((PsiElementUsageTarget)usageTargets[0]).getElement();
      startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
    }
  }

  private void startFindUsages(PsiElement element, RelativePoint popupPosition, Editor editor, int maxUsages) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    if (showSettingsDialogBefore) {
      showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
      return;
    }
    showElementUsages(handler, editor, popupPosition, maxUsages);
  }

  private void showElementUsages(FindUsagesHandler handler, final Editor editor, final RelativePoint popupPosition, final int maxUsages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);

    final UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(handler.getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null);

    final List<Usage> usages = new ArrayList<Usage>();
    final Set<UsageNode> visibleNodes = new LinkedHashSet<UsageNode>();
    Processor<Usage> collect = new Processor<Usage>() {
      public boolean process(@NotNull Usage usage) {
        synchronized (usages) {
          if (visibleNodes.size() > maxUsages) return false;
          UsageNode node = usageView.doAppendUsage(usage);
          if (node != null) {
            if (visibleNodes.size() == maxUsages) {
              usageView.removeUsage(usage);
              visibleNodes.add(UsageViewImpl.NULL_NODE);
              return false;
            }
            visibleNodes.add(node);
          }
          usages.add(usage);
        }
        return true;
      }
    };
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(handler.getProject())).getFindUsagesManager();
    presentation = findUsagesManager.processUsages(handler, collect);
    if (presentation == null) {
      Disposer.dispose(usageView);
      return;
    }
    final String title = presentation.getTabText();

    JBPopup popup = createUsagePopup(usages, visibleNodes, title, handler, editor, popupPosition, maxUsages, usageView);
    if (popup != null) {
      popup.show(popupPosition);
    }
  }

  private void showHint(String text, final Editor editor, final RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages) {
    JComponent label = createHintComponent(text, handler, popupPosition, editor, new Runnable() {
      public void run() {
        HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
      }
    }, maxUsages);
    if (editor == null) {
      HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY |
                                                               HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
    }
    else {
      HintManager.getInstance().showInformationHint(editor, label);
    }
  }

  private JComponent createHintComponent(String text, final FindUsagesHandler handler, final RelativePoint popupPosition, final Editor editor,
                                         final Runnable cancelAction,
                                         int maxUsages) {
    JLabel label = HintUtil.createInformationLabel(text);
    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction);
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

  private InplaceButton createSettingsButton(final FindUsagesHandler handler, final RelativePoint popupPosition,
                                             final Editor editor,
                                             final int maxUsages,
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
            showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
          }
        });
        cancelAction.run();
      }
    });
  }

  private void showDialogAndFindUsages(FindUsagesHandler handler, RelativePoint popupPosition, Editor editor, int maxUsages) {
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(false, false, false);
    dialog.show();
    if (dialog.isOK()) {
      dialog.calcFindUsagesOptions();
      showElementUsages(handler, editor, popupPosition, maxUsages);
    }
  }

  private static String searchScopePresentableName(final FindUsagesHandler handler) {
    SearchScope searchScope = FindUsagesManager.getCurrentSearchScope(handler);
    if (searchScope == null) searchScope = ProjectScope.getAllScope(handler.getProject());
    return searchScope.getDisplayName();
  }

  private JBPopup createUsagePopup(final List<Usage> usages,
                                   Set<UsageNode> visibleNodes,
                                   final String title,
                                   final FindUsagesHandler handler,
                                   final Editor editor,
                                   final RelativePoint popupPosition,
                                   final int maxUsages,
                                   final UsageViewImpl usageView) {
    boolean hasMore = visibleNodes.remove(UsageViewImpl.NULL_NODE);

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    final UsageViewSettings save = new UsageViewSettings();

    save.loadState(usageViewSettings);
    usageViewSettings.GROUP_BY_FILE_STRUCTURE = false;
    usageViewSettings.GROUP_BY_MODULE = false;
    usageViewSettings.GROUP_BY_PACKAGE = false;
    usageViewSettings.GROUP_BY_USAGE_TYPE = false;

    final Project project = handler.getProject();
    Disposer.register(usageView, new Disposable() {
      public void dispose() {
        usageViewSettings.GROUP_BY_FILE_STRUCTURE = save.GROUP_BY_FILE_STRUCTURE;
        usageViewSettings.GROUP_BY_MODULE = save.GROUP_BY_MODULE;
        usageViewSettings.GROUP_BY_PACKAGE = save.GROUP_BY_PACKAGE;
        usageViewSettings.GROUP_BY_USAGE_TYPE = save.GROUP_BY_USAGE_TYPE;
      }
    });

    if (visibleNodes.isEmpty()) {
      if (usages.isEmpty()) {
        String text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(handler));
        showHint(text, editor, popupPosition, handler, maxUsages);
        Disposer.dispose(usageView);
        return null;
      }
      else {
        // all usages filtered out
      }
    }
    if (visibleNodes.size() == 1 && usages.size() == 1) {
      //the only usage
      Usage usage = visibleNodes.iterator().next().getUsage();
      navigateAndHint(usage, UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(handler)), handler, popupPosition,
                      maxUsages);
      Disposer.dispose(usageView);
      return null;
    }
    if (visibleNodes.size() == 1 && usages.size() >= 1) {
      // usage view can filter usages down to one
      Usage usage = visibleNodes.iterator().next().getUsage();
      navigateAndHint(usage, UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScopePresentableName(handler)),
                      handler, popupPosition, maxUsages);
      Disposer.dispose(usageView);
      return null;
    }

    if (hasMore) {
      usages.add(NullUsage.INSTANCE);
      visibleNodes.add(UsageViewImpl.NULL_NODE);
    }
    addUsageNodes(usageView.getRoot(), usageView, new ArrayList<UsageNode>());
    Vector<Object> data = createListModel(visibleNodes, usages);
    final JList list = new JList(data);
    list.setCellRenderer(new ShowUsagesListCellRenderer(usageView));

    final Runnable navigateRunnable = new Runnable() {
      public void run() {
        Object value = list.getSelectedValue();
        if (value instanceof UsageNode) {
          Usage usage = ((UsageNode)value).getUsage();
          if (usage == NullUsage.INSTANCE) {
            appendMoreUsages(editor, popupPosition, handler, maxUsages);
            return;
          }
          navigateAndHint(usage, null, handler, popupPosition, maxUsages);
        }
      }
    };

    ListSpeedSearch speedSearch = new ListSpeedSearch(list, SPEED_SEARCH_TEXT);
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator(false));

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      String s;
      if (hasMore) {
        s = "<html><body><b>Some</b> " + title + " " + "<b>(Only " + (visibleNodes.size() - 1) + " usages shown)</b></body></html>";
      }
      else {
        s = title + " (" + usages.size() + " usages found)";
      }
      builder.setTitle(s);
    }

    builder.setMovable(true).setResizable(true);
    builder.setItemChoosenCallback(navigateRunnable);
    final JBPopup[] popup = new JBPopup[1];
    ActionListener editSettings = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        popup[0].cancel();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
          }
        });
      }
    };

    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      builder.registerKeyboardAction(shortcut.getFirstKeyStroke(), editSettings);
    }

    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, new Runnable() {
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
        rebuildPopup(usageView, usages, list, popup[0]);
      }
    });

    return popup[0];
  }

  private static void rebuildPopup(final UsageViewImpl usageView, final List<Usage> usages, final JList list, final JBPopup popup) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final List<UsageNode> nodes = new ArrayList<UsageNode>();
        addUsageNodes(usageView.getRoot(), usageView, nodes);

        Vector<Object> data = createListModel(nodes, usages);
        Dimension oldPreferred = list.getPreferredSize();
        list.setListData(data);
        list.setVisibleRowCount(data.size());
        Dimension newPreferred = list.getPreferredSize();

        JComponent content = popup.getContent();
        Window window = SwingUtilities.windowForComponent(content);

        Dimension d = new Dimension(window.getSize());
        d.setSize(d.width + newPreferred.width - oldPreferred.width, d.height + newPreferred.height - oldPreferred.height);
        window.setSize(d);
        window.validate();
        window.repaint();
      }
    });
  }

  private void appendMoreUsages(Editor editor, RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages) {
    showElementUsages(handler,  editor, popupPosition, maxUsages+USAGES_PAGE_SIZE);

    //List<Usage> usages = new ArrayList<Usage>();
    //UsageViewPresentation presentation =
    //  processUsages(usages, handler, maxUsages + USAGES_PAGE_SIZE);
    //if (presentation == null) {
    //  popup.cancel();
    //  return;
    //}
    //usageView.reset();
    //for (Usage usage : usages) {
    //  usageView.appendUsage(usage);
    //}
    //rebuildPopup(usageView, usages, list, popup);
  }

  private static Vector<Object> createListModel(Collection<UsageNode> nodes, List<Usage> usages) {
    return nodes.isEmpty() ? new Vector<Object>(Collections.singletonList(UsageViewBundle.message("usages.were.filtered.out", usages.size()))) : new Vector<Object>(nodes);
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


  private static void addUsageNodes(GroupNode root, final UsageViewImpl usageView, List<UsageNode> outNodes) {
    for (UsageNode node : root.getUsageNodes()) {
      Usage usage = node.getUsage();
      if (usageView.isVisible(usage)) {
        node.setParent(root);
        outNodes.add(node);
      }
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, usageView, outNodes);
    }
  }

  public void update(AnActionEvent e){
    FindUsagesInFileAction.updateFindUsagesAction(e);
  }

  private void navigateAndHint(Usage usage, final String hint, final FindUsagesHandler handler,
                               final RelativePoint popupPosition,
                               final int maxUsages) {
    usage.navigate(true);
    if (hint == null) return;
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    final Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
    if (newEditor != null) {
      final Project project = handler.getProject();
      //opening editor is performing in invokeLater
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
            public void run() {
              // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
                public void run() {
                  showHint(hint, newEditor, popupPosition, handler, maxUsages);
                }
              });
            }
          });
        }
      });
    }
  }
}
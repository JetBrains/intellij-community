package com.intellij.find.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usages.*;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.CommonProcessors;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class ShowUsagesAction extends AnAction {
  public ShowUsagesAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (usageTargets == null) {
      FindUsagesAction.chooseAmbiguousTargetAndPerform(project, editor, new PsiElementProcessor<PsiElement>() {
        public boolean execute(final PsiElement element) {
          showElementUsages(project, element, editor, popupPosition);
          return false;
        }
      });
    }
    else {
      PsiElement element = ((PsiElement2UsageTargetAdapter)usageTargets[0]).getElement();
      showElementUsages(project, element, editor, popupPosition);
    }
  }

  private static void showElementUsages(@NotNull Project project, final PsiElement element, Editor editor, final RelativePoint popupPosition) {
    ArrayList<Usage> usages = new ArrayList<Usage>();
    CommonProcessors.CollectProcessor<Usage> collect = new CommonProcessors.CollectProcessor<Usage>(usages);
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    UsageViewPresentation presentation = findUsagesManager.processUsages(element, collect, handler);
    if (presentation == null) return;
    if (usages.isEmpty()) {
      String text = FindBundle.message("no.usages.found.in", searchScopePresentableName(element, handler));
      if (editor != null) {
        HintManager.getInstance().showInformationHint(editor, text);
      }
      else {
        JLabel label = HintUtil.createInformationLabel(text);
        HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
      }
    }
    else {
      final String title = presentation.getTabText();
      JBPopup popup = getUsagePopup(usages, title, project, element, handler);
      if (popup != null) {
        popup.show(popupPosition);
      }
    }
  }

  private static String searchScopePresentableName(PsiElement element, final FindUsagesHandler handler) {
    SearchScope searchScope = FindUsagesManager.getCurrentSearchScope(handler);
    if (searchScope == null) searchScope = ProjectScope.getAllScope(element.getProject());
    return searchScope.getDisplayName();
  }

  private static JBPopup getUsagePopup(List<Usage> usages, final String title, final Project project, PsiElement element,
                                       final FindUsagesHandler handler) {
    Usage[] arr = usages.toArray(new Usage[usages.size()]);
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);
    final UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(project).createUsageView(new UsageTarget[0], arr, presentation, null);

    GroupNode root = usageView.getRoot();
    List<UsageNode> nodes = new ArrayList<UsageNode>();
    Set<Usage> filteredUsages = new THashSet<Usage>();

    addUsageNodes(root, nodes, usageView, filteredUsages);
    if (nodes.size() == 1) {
      // usage view can filter usages down to one
      Usage usage = nodes.get(0).getUsage();
      if (filteredUsages.size() == 1) {
        navigateAndHint(usage, FindBundle.message("show.usages.only.usage", searchScopePresentableName(element, handler)));
      }
      else {
        navigateAndHint(usage, FindBundle.message("all.usages.are.in.this.line", filteredUsages.size(), searchScopePresentableName(element, handler)));
      }
      usageView.dispose();
      return null;
    }

    final JList list = new JList(new Vector<UsageNode>(nodes));
    list.setCellRenderer(new ListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JPanel panel = new JPanel(new GridBagLayout());
        UsageNode usageNode = (UsageNode)value;
        int seq = appendGroupText((GroupNode)usageNode.getParent(), panel,list, value, index, isSelected);

        ColoredListCellRenderer usageRenderer = new ColoredListCellRenderer() {
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            UsageNode usageNode = (UsageNode)value;
            Usage usage = usageNode.getUsage();
            UsagePresentation presentation = usage.getPresentation();
            setIcon(presentation.getIcon());

            TextChunk[] text = presentation.getText();
            for (TextChunk textChunk : text) {
              append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
            }
          }
        };
        usageRenderer.setIpad(new Insets(0,0,0,0));
        usageRenderer.setBorder(null);
        usageRenderer.getListCellRendererComponent(list, value, index, isSelected, false);
        panel.add(usageRenderer, new GridBagConstraints(seq, 0, GridBagConstraints.REMAINDER, 0, 1, 0,
                                                        GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 1));
        panel.setBackground(list.getBackground());
        return panel;
      }
      private int appendGroupText(final GroupNode node, JPanel panel, JList list, Object value, int index, boolean isSelected) {
        if (node != null && node.getGroup() != null) {
          int seq = appendGroupText((GroupNode)node.getParent(), panel, list, value, index, isSelected);
          if (node.canNavigateToSource()) {
            ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
              protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                UsageGroup group = node.getGroup();
                setIcon(group.getIcon(false));
                append(group.getText(usageView), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
            };
            renderer.setIpad(new Insets(0,0,0,0));
            renderer.setBorder(null);
            renderer.getListCellRendererComponent(list, value, index, isSelected, false);
            panel.add(renderer, new GridBagConstraints(seq, 0, 1, 0, 0, 0,
                                                       GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 1));
            return seq+1;
          }
        }
        return 0;
      }
    });

    final Runnable runnable = new Runnable() {
      public void run() {
        Object element = list.getSelectedValue();
        if (element == null) return;
        UsageNode node = (UsageNode)element;
        Usage usage = node.getUsage();
        navigateAndHint(usage, null);
      }
    };

    ListSpeedSearch speedSearch = new ListSpeedSearch(list) {
      protected String getElementText(final Object element) {
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
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator() {
      public void translatePattern(final StringBuilder buf, final String pattern) {
        final int len = pattern.length();
        for (int i = 0; i < len; ++i) {
          translateCharacter(buf, pattern.charAt(i));
        }
      }
    });

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title + " " +FindBundle.message("some.usages.found", usages.size()));
    }

    final JBPopup popup = builder.setItemChoosenCallback(runnable).createPopup();
    Disposer.register(popup, usageView);
    return popup;
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

  private static void navigateAndHint(Usage usage, final String hint) {
    usage.navigate(true);
    if (hint == null) return;
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    final Editor newEditor = newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
    if (newEditor != null) {
      //opening editor is performing in invokeLater
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
            public void run() {
              // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  HintManager.getInstance().showInformationHint(newEditor, hint);
                }
              });
            }
          });
        }
      });
    }
  }
}
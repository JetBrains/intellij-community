/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ui.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;

public class ScopeEditorPanel {

  private JPanel myButtonsPanel;
  private JTextField myPatternField;
  private JPanel myTreeToolbar;
  private final Tree myPackageTree;
  private JPanel myPanel;
  private JPanel myTreePanel;
  private JLabel myMatchingCountLabel;
  private JPanel myLegendPanel;

  private final Project myProject;
  private final TreeExpansionMonitor myTreeExpansionMonitor;
  private final Marker myTreeMarker;
  private PackageSet myCurrentScope = null;
  private boolean myIsInUpdate = false;
  private String myErrorMessage;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private JLabel myCaretPositionLabel;
  private int myCaretPosition = 0;
  private boolean myTextChanged = false;
  private JPanel myMatchingCountPanel;
  private PanelProgressIndicator myCurrentProgress;
  private final NamedScopesHolder myHolder;

  public ScopeEditorPanel(Project project) {
    this(project, null);
  }

  public ScopeEditorPanel(Project project, final NamedScopesHolder holder) {
    myProject = project;
    myHolder = holder;
    myButtonsPanel.add(createActionsPanel());

    myPackageTree = new Tree(new RootNode());
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myPackageTree), BorderLayout.CENTER);

    myTreeToolbar.setLayout(new BorderLayout());
    myTreeToolbar.add(createTreeToolbar(), BorderLayout.WEST);

    myTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myPackageTree, myProject);

    myTreeMarker = new Marker() {
      public boolean isMarked(PsiFile file) {
        return myCurrentScope != null && myCurrentScope.contains(file, getHolder());
      }
    };

    myPatternField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        onTextChange();
      }
    });

    myPatternField.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent e) {
        myCaretPosition = e.getDot();
        updateCaretPositionText();
      }
    });

    myPatternField.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        myCaretPositionLabel.setVisible(true);
      }

      public void focusLost(FocusEvent e) {
        myCaretPositionLabel.setVisible(false);
      }
    });

    initTree(myPackageTree);
  }

  private void updateCaretPositionText() {
    if (myErrorMessage != null) {
      myCaretPositionLabel.setText(IdeBundle.message("label.scope.editor.caret.position", myCaretPosition + 1));
    }
    else {
      myCaretPositionLabel.setText("");
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JPanel getTreePanel(){
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myTreePanel, BorderLayout.CENTER);
    panel.add(myLegendPanel, BorderLayout.SOUTH);
    return panel;
  }

  public JPanel getTreeToolbar() {
    return myTreeToolbar;
  }

  private void onTextChange() {
    if (!myIsInUpdate) {
      myUpdateAlarm.cancelAllRequests();
      myCurrentScope = null;
      try {
        myCurrentScope = PackageSetFactory.getInstance().compile(myPatternField.getText());
        myErrorMessage = null;
        myTextChanged = true;
        rebuild(false);
      }
      catch (Exception e) {
        myErrorMessage = e.getMessage();
        showErrorMessage();
      }
    }
    else {
      myErrorMessage = null;
    }
  }

  private void showErrorMessage() {
    myMatchingCountLabel.setText(StringUtil.capitalize(myErrorMessage));
    myMatchingCountLabel.setForeground(Color.red);
    myMatchingCountLabel.setToolTipText(myErrorMessage);
  }

  private JComponent createActionsPanel() {
    JButton include = new JButton(IdeBundle.message("button.include"));
    JButton includeRec = new JButton(IdeBundle.message("button.include.recursively"));
    JButton exclude = new JButton(IdeBundle.message("button.exclude"));
    JButton excludeRec = new JButton(IdeBundle.message("button.exclude.recursively"));

    JPanel buttonsPanel = new JPanel(new VerticalFlowLayout());
    buttonsPanel.add(include);
    buttonsPanel.add(includeRec);
    buttonsPanel.add(exclude);
    buttonsPanel.add(excludeRec);

    include.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelected(false);
      }
    });
    includeRec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelected(true);
      }
    });
    exclude.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelected(false);
      }
    });
    excludeRec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelected(true);
      }
    });

    return buttonsPanel;
  }

  private void excludeSelected(boolean recurse) {
    final ArrayList<PackageSet> selected = getSelectedSets(recurse);
    if (selected == null || selected.isEmpty()) return;
    for (PackageSet set : selected) {
      if (myCurrentScope == null) {
        myCurrentScope = new ComplementPackageSet(set);
      } else {
        myCurrentScope = new IntersectionPackageSet(myCurrentScope, new ComplementPackageSet(set));
      }
    }
    rebuild(true);
  }

  private void includeSelected(boolean recurse) {
    final ArrayList<PackageSet> selected = getSelectedSets(recurse);
    if (selected == null || selected.isEmpty()) return;
    for (PackageSet set : selected) {
      if (myCurrentScope == null) {
        myCurrentScope = set;
      }
      else {
        myCurrentScope = new UnionPackageSet(myCurrentScope, set);
      }
    }
    rebuild(true);
  }

  @Nullable
  private ArrayList<PackageSet> getSelectedSets(boolean recursively) {
    int[] rows = myPackageTree.getSelectionRows();
    if (rows == null) return null;
    final ArrayList<PackageSet> result = new ArrayList<PackageSet>();
    for (int row : rows) {
      final PackageDependenciesNode node = (PackageDependenciesNode)myPackageTree.getPathForRow(row).getLastPathComponent();
      final PackageSet set = PatternDialectProvider.getInstance(DependencyUISettings.getInstance().SCOPE_TYPE).createPackageSet(node, recursively);
      if (set != null) {
        result.add(set);
      }
    }
    return result;
  }


  private JComponent createTreeToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final Runnable update = new Runnable() {
      public void run() {
        rebuild(true);
      }
    };
    group.add(new FlattenPackagesAction(update));
    for (PatternDialectProvider provider : Extensions.getExtensions(PatternDialectProvider.EP_NAME)) {
      for (AnAction action : provider.createActions(update)) {
        group.add(action);
      }
    }
    group.add(new ShowFilesAction(update));
    group.add(new ShowModulesAction(update));
    group.add(new ShowModuleGroupsAction(update));
    group.add(new FilterLegalsAction(update));
    group.add(new ChooseScopeTypeAction(update));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private void rebuild(final boolean updateText, final Runnable runnable){
    myUpdateAlarm.cancelAllRequests();
    final Runnable request = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            myIsInUpdate = true;
            if (updateText && myCurrentScope != null) {
              myPatternField.setText(myCurrentScope.getText());
            }
            try {
              if (!myProject.isDisposed()) {
                updateTreeModel();
              }
            }
            catch (ProcessCanceledException e) {
              return;
            }
            if (runnable != null) {
              runnable.run();
            }
            myIsInUpdate = false;
          }
        });
      }
    };
    myUpdateAlarm.addRequest(request, 1000);
  }

  private void rebuild(final boolean updateText) {
    rebuild(updateText, null);
  }

  private static void initTree(Tree tree) {
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    new TreeSpeedSearch(tree);
  }

  private void updateTreeModel() throws ProcessCanceledException {
    PanelProgressIndicator progress = new PanelProgressIndicator(new Consumer<JComponent>() {
      public void consume(final JComponent component) {
        setToComponent(component);
      }
    }) {
      public void start() {
        super.start();
        myTextChanged = false;
      }

      public boolean isCanceled() {
        return super.isCanceled() || myTextChanged || !myPanel.isShowing();
      }

      public void stop() {
        super.stop();
        setToComponent(myMatchingCountLabel);
      }

      public String getText() { //just show non-blocking progress
        return null;
      }

      public String getText2() {
        return null;
      }
    };
    progress.setBordersVisible(false); 
    myCurrentProgress = progress;
    Runnable updateModel = new Runnable() {
      public void run() {
        final ProcessCanceledException [] ex = new ProcessCanceledException[1];
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            try {
              myTreeExpansionMonitor.freeze();
              final TreeModel model = PatternDialectProvider.getInstance(DependencyUISettings.getInstance().SCOPE_TYPE).createTreeModel(myProject, myTreeMarker);
              if (myErrorMessage == null) {
                myMatchingCountLabel
                  .setText(IdeBundle.message("label.scope.contains.files", model.getMarkedFileCount(), model.getTotalFileCount()));
                myMatchingCountLabel.setForeground(new JLabel().getForeground());
              }
              else {
                showErrorMessage();
              }

              SwingUtilities.invokeLater(new Runnable(){
                public void run() { //not under progress
                  myPackageTree.setModel(model);
                  myTreeExpansionMonitor.restore();
                }
              });
            } catch (ProcessCanceledException e) {
              ex[0] = e;
            }
            finally {
              myCurrentProgress = null;
              //update label
              setToComponent(myMatchingCountLabel);
            }
          }
        });
        if (ex[0] != null) {
          throw ex[0];
        }
      }
    };
    ProgressManager.getInstance().runProcess(updateModel, progress);
  }

  public void cancelCurrentProgress(){
    if (myCurrentProgress != null && myCurrentProgress.isRunning()){
      myCurrentProgress.cancel();
    }
  }

  public void apply() throws ConfigurationException {
    if (myCurrentScope == null) {
      throw new ConfigurationException(IdeBundle.message("error.correct.pattern.syntax.errors.first"));
    }
  }

  public PackageSet getCurrentScope() {
    return myCurrentScope;
  }

  public void reset(PackageSet packageSet, Runnable runnable){
    myCurrentScope = packageSet;
    myPatternField.setText(myCurrentScope == null ? "" : myCurrentScope.getText());
    rebuild(false, runnable);
  }

  private void setToComponent(final JComponent cmp) {
    myMatchingCountPanel.removeAll();
    myMatchingCountPanel.add(cmp, BorderLayout.CENTER);
    myMatchingCountPanel.revalidate();
    myMatchingCountPanel.repaint();
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        myPatternField.requestFocusInWindow();        
      }
    });
  }

  public void restoreCanceledProgress() {
    if (myIsInUpdate) {
      rebuild(false);
    }
  }

  public void clearCaches() {
    FileTreeModelBuilder.clearCaches(myProject);
  }

  public NamedScopesHolder getHolder() {
    return myHolder;
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    private static final Color WHOLE_INCLUDED = new Color(10, 119, 0);
    private static final Color PARTIAL_INCLUDED = new Color(0, 50, 160);

    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof PackageDependenciesNode) {
        PackageDependenciesNode node = (PackageDependenciesNode)value;
        if (expanded) {
          setIcon(node.getOpenIcon());
        }
        else {
          setIcon(node.getClosedIcon());
        }

        setForeground(selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground());
        if (!selected && node.hasMarked() && !DependencyUISettings.getInstance().UI_FILTER_LEGALS) {
          setForeground(node.hasUnmarked() ? PARTIAL_INCLUDED : WHOLE_INCLUDED);
        }
        if (node instanceof DirectoryNode) {
          final DirectoryNode directoryNode = (DirectoryNode)node;
          append(directoryNode.getDirName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          final String locationString = directoryNode.getLocationString();
          if (locationString != null) {
            append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
        else {
          append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    }
  }

  private final class ChooseScopeTypeAction extends ComboBoxAction{
    private final Runnable myUpdate;

    public ChooseScopeTypeAction(final Runnable update) {
      myUpdate = update;
    }

    @NotNull
    protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final PatternDialectProvider provider : Extensions.getExtensions(PatternDialectProvider.EP_NAME)) {
        group.add(new AnAction(provider.getDisplayName()) {
          public void actionPerformed(final AnActionEvent e) {
            DependencyUISettings.getInstance().SCOPE_TYPE = provider.getShortName();
            myUpdate.run();
          }
        });
      }
      return group;
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final PatternDialectProvider provider = PatternDialectProvider.getInstance(DependencyUISettings.getInstance().SCOPE_TYPE);
      e.getPresentation().setText(provider.getDisplayName());
      e.getPresentation().setIcon(provider.getIcon());
    }
  }

  private final class FilterLegalsAction extends ToggleAction {
    private final Runnable myUpdate;

    public FilterLegalsAction(final Runnable update) {
      super(IdeBundle.message("action.show.included.only"),
            IdeBundle.message("action.description.show.included.only"), IconLoader.getIcon("/ant/filter.png"));
      myUpdate = update;
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_FILTER_LEGALS;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      UIUtil.setEnabled(myLegendPanel, !flag, true);
      myUpdate.run();
    }
  }
}
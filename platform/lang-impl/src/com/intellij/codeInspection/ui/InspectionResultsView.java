/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offlineViewer.OfflineInspectionRVContentProvider;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.actions.ExportHTMLAction;
import com.intellij.codeInspection.ui.actions.InvokeQuickFixAction;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements Disposable, OccurenceNavigator, DataProvider {
  private static final Logger LOG = Logger.getInstance(InspectionResultsView.class);

  public static final DataKey<InspectionResultsView> DATA_KEY = DataKey.create("inspectionView");
  private static final Key<Boolean> PREVIEW_EDITOR_IS_REUSED_KEY = Key.create("inspection.tool.window.preview.editor.is.reused");

  private final Project myProject;
  private final InspectionTree myTree;
  private final ConcurrentMap<HighlightDisplayLevel, ConcurrentMap<String, InspectionGroupNode>> myGroups =
    ContainerUtil.newConcurrentMap();
  private final OccurenceNavigator myOccurenceNavigator;
  private volatile InspectionProfile myInspectionProfile;
  @NotNull
  private final AnalysisScope myScope;
  @NonNls
  private static final String HELP_ID = "reference.toolWindows.inspections";
  private final ConcurrentMap<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = ContainerUtil.newConcurrentMap();

  private final Splitter mySplitter;
  @NotNull
  private final GlobalInspectionContextImpl myGlobalInspectionContext;
  private boolean myRerun;
  private volatile boolean myDisposed;
  private int myUpdatingRequestors; //accessed only in edt
  private boolean myApplyingFix; //accessed only in edt

  @NotNull
  private final InspectionRVContentProvider myProvider;
  private final ExclusionHandler<InspectionTreeNode> myExclusionHandler;
  private EditorEx myPreviewEditor;
  private InspectionTreeLoadingProgressAware myLoadingProgressPreview;
  private final ExcludedInspectionTreeNodesManager myExcludedInspectionTreeNodesManager;
  private final FactoryMap<String, Set<Object>> mySuppressedNodes = new FactoryMap<String, Set<Object>>() {
    @Nullable
    @Override
    protected Set<Object> create(String key) {
      return new THashSet<>();
    }
  };
  private final ConcurrentMap<String, Set<SuppressIntentionAction>> mySuppressActions = new ConcurrentHashMap<>();

  private final Object myTreeStructureUpdateLock = new Object();

  public InspectionResultsView(@NotNull GlobalInspectionContextImpl globalInspectionContext,
                               @NotNull InspectionRVContentProvider provider) {
    setLayout(new BorderLayout());
    myProject = globalInspectionContext.getProject();
    myInspectionProfile = globalInspectionContext.getCurrentProfile();
    myScope = globalInspectionContext.getCurrentScope();
    myGlobalInspectionContext = globalInspectionContext;
    myProvider = provider;
    myExcludedInspectionTreeNodesManager = new ExcludedInspectionTreeNodesManager(provider instanceof OfflineInspectionRVContentProvider,
                                                                                  isSingleInspectionRun());

    myTree = new InspectionTree(myProject, globalInspectionContext, this);
    initTreeListeners();

    myOccurenceNavigator = initOccurenceNavigator();

    mySplitter = new OnePixelSplitter(false, AnalysisUIOptions.getInstance(myProject).SPLITTER_PROPORTION);
    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT));
    mySplitter.setHonorComponentsMinimumSize(false);

    mySplitter.addPropertyChangeListener(evt -> {
      if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
        myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
      }
    });
    add(mySplitter, BorderLayout.CENTER);
    myExclusionHandler = new ExclusionHandler<InspectionTreeNode>() {
      @Override
      public boolean isNodeExclusionAvailable(@NotNull InspectionTreeNode node) {
        return true;
      }

      @Override
      public boolean isNodeExcluded(@NotNull InspectionTreeNode node) {
        return node.isExcluded(myExcludedInspectionTreeNodesManager);
      }

      @Override
      public void excludeNode(@NotNull InspectionTreeNode node) {
        node.excludeElement(myExcludedInspectionTreeNodesManager);
      }

      @Override
      public void includeNode(@NotNull InspectionTreeNode node) {
        node.amnestyElement(myExcludedInspectionTreeNodesManager);
      }

      @Override
      public boolean isActionEnabled(boolean isExcludeAction) {
        return isExcludeAction || !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS;
      }

      @Override
      public void onDone(boolean isExcludeAction) {
        if (isExcludeAction) {
          if (myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS) {
            synchronized (myTreeStructureUpdateLock) {
              final TreePath[] selected = myTree.getSelectionPaths();
              if (selected != null) {
                final TreePath commonParent = TreeUtil.findCommonPath(selected);
                if (!selectCommonNextSibling(selected, commonParent)) {
                  TreeUtil.removeSelected(myTree);
                  TreeUtil.selectPath(myTree, commonParent);
                }
              }
            }
          }
          myTree.revalidate();
          myTree.repaint();
        }
        else {
          InspectionViewPsiTreeChangeAdapter.resetTree(InspectionResultsView.this);
        }
        syncRightPanel();
      }

      private boolean selectCommonNextSibling(@NotNull TreePath[] selected, @NotNull TreePath commonParent) {
        final int pathCount = commonParent.getPathCount() + 1;
        for (TreePath path : selected) {
          if (path.getPathCount() != pathCount) {
            return false;
          }
        }
        final InspectionTreeNode parent = (InspectionTreeNode)commonParent.getLastPathComponent();
        final int[] indices = new int[selected.length];
        for (int i = 0; i < selected.length; i++) {
          TreePath path = selected[i];
          indices[i] = parent.getIndex((TreeNode)path.getLastPathComponent());
        }
        Arrays.sort(indices);
        int prevIdx = -1;
        for (int idx: indices) {
          if (prevIdx != -1) {
            if (idx - prevIdx != 1) {
              return false;
            }
          }
          prevIdx = idx;
        }
        final int toSelect = indices[indices.length - 1] + 1;
        if (parent.getChildCount() > toSelect) {
          final TreeNode nodeToSelect = parent.getChildAt(toSelect);
          TreeUtil.removeSelected(myTree);
          TreeUtil.selectNode(myTree, nodeToSelect);
          return true;
        }
        return false;
      }
    };
    createActionsToolbar();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new InspectionViewPsiTreeChangeAdapter(this), this);

    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
    profileManager.addProfileChangeListener(new ProfileChangeAdapter() {
      @Override
      public void profileChanged(Profile profile) {
        if (profile == profileManager.getCurrentProfile()) {
          myTree.revalidate();
          myTree.repaint();
          syncRightPanel();
        }
      }
    }, this);
  }

  private void initTreeListeners() {
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myTree.isUnderQueueUpdate()) return;
        syncRightPanel();
        if (isAutoScrollMode()) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);

    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
      }
    });

    PopupHandler.installPopupHandler(myTree, IdeActions.INSPECTION_TOOL_WINDOW_TREE_POPUP, ActionPlaces.CODE_INSPECTION);
    SmartExpander.installOn(myTree);
  }

  private OccurenceNavigatorSupport initOccurenceNavigator() {
    return new OccurenceNavigatorSupport(myTree) {
      @Override
      @Nullable
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (node instanceof InspectionTreeNode && ((InspectionTreeNode)node).isExcluded(myExcludedInspectionTreeNodesManager)) {
          return null;
        }
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return null;
          final RefEntity element = refNode.getElement();
          if (element == null || !element.isValid()) return null;
          final CommonProblemDescriptor problem = refNode.getDescriptor();
          if (problem != null) {
            return navigate(problem);
          }
          if (element instanceof RefElement) {
            return getOpenFileDescriptor((RefElement)element);
          }
        }
        else if (node instanceof ProblemDescriptionNode) {
          boolean isValid;
          if (((ProblemDescriptionNode)node).isValid()) {
            if (((ProblemDescriptionNode)node).isQuickFixAppliedFromView()) {
              isValid = ((ProblemDescriptionNode)node).calculateIsValid();
            } else {
              isValid = true;
            }
          } else {
            isValid = false;
          }
          return isValid
                 ? navigate(((ProblemDescriptionNode)node).getDescriptor())
                 : InspectionResultsViewUtil.getNavigatableForInvalidNode((ProblemDescriptionNode)node);
        }
        return null;
      }

      @Nullable
      private Navigatable navigate(final CommonProblemDescriptor descriptor) {
        return getSelectedNavigatable(descriptor);
      }

      @Override
      public String getNextOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.next");
      }

      @Override
      public String getPreviousOccurenceActionName() {
        return InspectionsBundle.message("inspection.actiongo.prev");
      }
    };
  }

  private void createActionsToolbar() {
    final JComponent leftActionsToolbar = createLeftActionsToolbar();
    final JComponent rightActionsToolbar = createRightActionsToolbar();

    JPanel westPanel = new JPanel(new BorderLayout());
    westPanel.add(leftActionsToolbar, BorderLayout.WEST);
    westPanel.add(rightActionsToolbar, BorderLayout.EAST);
    add(westPanel, BorderLayout.WEST);
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private JComponent createRightActionsToolbar() {
    DefaultActionGroup specialGroup = new DefaultActionGroup();
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupByDirectoryAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    specialGroup.add(myGlobalInspectionContext.createToggleAutoscrollAction());
    specialGroup.add(new ExportHTMLAction(this));
    specialGroup.add(ActionManager.getInstance().getAction("EditInspectionSettings"));
    specialGroup.add(new InvokeQuickFixAction(this));
    return createToolbar(specialGroup);
  }

  private JComponent createLeftActionsToolbar() {
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RerunAction(this));
    group.add(new CloseAction());
    final TreeExpander treeExpander = new TreeExpander() {
      @Override
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    };
    group.add(actionsManager.createExpandAllAction(treeExpander, myTree));
    group.add(actionsManager.createCollapseAllAction(treeExpander, myTree));
    group.add(actionsManager.createPrevOccurenceAction(getOccurenceNavigator()));
    group.add(actionsManager.createNextOccurenceAction(getOccurenceNavigator()));
    group.add(new ContextHelpAction(HELP_ID));

    return createToolbar(group);
  }

  private JComponent createToolbar(final DefaultActionGroup specialGroup) {
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false);
    //toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  @Override
  public void dispose() {
    InspectionResultsViewUtil.releaseEditor(myPreviewEditor);
    mySplitter.dispose();
    myInspectionProfile = null;
    myDisposed = true;
    if (myLoadingProgressPreview != null) {
      Disposer.dispose(myLoadingProgressPreview);
      myLoadingProgressPreview = null;
    }
  }


  private boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(myProject).getActiveToolWindowId();
    return myGlobalInspectionContext.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  @Nullable
  private static OpenFileDescriptor getOpenFileDescriptor(final RefElement refElement) {
    final VirtualFile[] file = new VirtualFile[1];
    final int[] offset = new int[1];

    ApplicationManager.getApplication().runReadAction(() -> {
      PsiElement psiElement = refElement.getElement();
      if (psiElement != null) {
        final PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile != null) {
          file[0] = containingFile.getVirtualFile();
          offset[0] = psiElement.getTextOffset();
        }
      }
      else {
        file[0] = null;
      }
    });

    if (file[0] != null && file[0].isValid()) {
      return new OpenFileDescriptor(refElement.getRefManager().getProject(), file[0], offset[0]);
    }
    return null;
  }

  public void setApplyingFix(boolean applyingFix) {
    myApplyingFix = applyingFix;
    syncRightPanel();
  }

  public void openRightPanelIfNeed() {
    if (mySplitter.getSecondComponent() == null) {
      syncRightPanel();
    }
  }

  public void syncRightPanel() {
    final Editor oldEditor = myPreviewEditor;
    if (myLoadingProgressPreview != null) {
      Disposer.dispose(myLoadingProgressPreview);
      myLoadingProgressPreview = null;
    }
    if (myApplyingFix) {
      final InspectionToolWrapper wrapper = myTree.getSelectedToolWrapper(true);
      LOG.assertTrue(wrapper != null);
      mySplitter.setSecondComponent(InspectionResultsViewUtil.getApplyingFixLabel(wrapper));
    } else {
      if (myTree.getSelectionModel().getSelectionCount() != 1) {
        if (myTree.getSelectedToolWrapper(true) == null) {
          mySplitter.setSecondComponent(InspectionResultsViewUtil.getNothingToShowTextLabel());
        }
        else {
          showInRightPanel(myTree.getCommonSelectedElement());
        }
      }
      else {
        TreePath pathSelected = myTree.getSelectionModel().getLeadSelectionPath();
        if (pathSelected != null) {
          final InspectionTreeNode node = (InspectionTreeNode)pathSelected.getLastPathComponent();
          if (node instanceof ProblemDescriptionNode) {
            final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
            showInRightPanel(problemNode.getElement());
          }
          else if (node instanceof InspectionPackageNode ||
                   node instanceof InspectionModuleNode ||
                   node instanceof RefElementNode) {
            showInRightPanel(node.getContainingFileLocalEntity());
          }
          else if (node instanceof InspectionNode) {
            if (myGlobalInspectionContext.getPresentation(((InspectionNode)node).getToolWrapper()).isDummy()) {
              mySplitter.setSecondComponent(InspectionResultsViewUtil.getNothingToShowTextLabel());
            }
            else {
              showInRightPanel(null);
            }
          }
          else if (node instanceof InspectionGroupNode || node instanceof InspectionSeverityGroupNode) {
            final InspectionViewNavigationPanel panel = new InspectionViewNavigationPanel(node, myTree);
            myLoadingProgressPreview = panel;
            mySplitter.setSecondComponent(panel);
          }
          else {
            LOG.error("Unexpected node: " + node.getClass());
          }
        }
      }
    }
    if (oldEditor != null) {
      if (Boolean.TRUE.equals(oldEditor.getUserData(PREVIEW_EDITOR_IS_REUSED_KEY))) {
        oldEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, null);
      }
      else {
        InspectionResultsViewUtil.releaseEditor(oldEditor);
        if (oldEditor == myPreviewEditor) {
          myPreviewEditor = null;
        }
      }
    }
  }

  private void showInRightPanel(@Nullable final RefEntity refEntity) {
    Cursor currentCursor = getCursor();
    try {
      setCursor(new Cursor(Cursor.WAIT_CURSOR));
      final JPanel editorPanel = new JPanel();
      editorPanel.setLayout(new BorderLayout());
      final int problemCount = myTree.getSelectedProblemCount(true);
      JComponent previewPanel = null;
      final InspectionToolWrapper tool = myTree.getSelectedToolWrapper(true);
      if (tool != null && refEntity != null && refEntity.isValid()) {
        final InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(tool);
        previewPanel = presentation.getCustomPreviewPanel(refEntity);
      }
      EditorEx previewEditor = null;
      if (previewPanel == null) {
        final Pair<JComponent, EditorEx> panelAndEditor = createBaseRightComponentFor(problemCount, refEntity);
        previewPanel = panelAndEditor.getFirst();
        previewEditor = panelAndEditor.getSecond();
      }
      editorPanel.add(previewPanel, BorderLayout.CENTER);
      if (problemCount > 0) {
        final JComponent fixToolbar = QuickFixPreviewPanelFactory.create(this);
        if (fixToolbar != null) {
          if (fixToolbar instanceof InspectionTreeLoadingProgressAware) {
            myLoadingProgressPreview = (InspectionTreeLoadingProgressAware)fixToolbar;
          }
          if (previewEditor != null) {
            previewPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
          }
          editorPanel.add(fixToolbar, BorderLayout.NORTH);
        }
      }
      if (previewEditor != null) {
        new ProblemPreviewEditorPresentation(previewEditor, this);
      }
      mySplitter.setSecondComponent(editorPanel);
    }
    finally {
      setCursor(currentCursor);
    }
  }

  private Pair<JComponent, EditorEx> createBaseRightComponentFor(int problemCount, RefEntity selectedEntity) {
    if (selectedEntity instanceof RefElement &&
        selectedEntity.isValid() &&
        !(((RefElement)selectedEntity).getElement() instanceof PsiDirectory)) {
      PsiElement selectedElement = ((RefElement)selectedEntity).getElement();
      if (problemCount == 1) {
        CommonProblemDescriptor[] descriptors = myTree.getSelectedDescriptors();
        if (descriptors.length != 0) {
          final CommonProblemDescriptor descriptor = descriptors[0];
          if (descriptor instanceof ProblemDescriptorBase) {
            final PsiElement element = ((ProblemDescriptorBase)descriptor).getPsiElement();
            if (element != null) {
              selectedElement = element;
            }
          }
        }
      }
      final PsiFile file = selectedElement.getContainingFile();
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return Pair.create(InspectionResultsViewUtil.createLabelForText("Can't open preview for \'" + file.getName() + "\'"), null);
      }

      if (reuseEditorFor(document)) {
        myPreviewEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, true);
        myPreviewEditor.getFoldingModel().runBatchFoldingOperation(() -> {
          myPreviewEditor.getFoldingModel().clearFoldRegions();
        });
        myPreviewEditor.getMarkupModel().removeAllHighlighters();
      }
      else {
        myPreviewEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject, file.getVirtualFile(), true);
        DiffUtil.setFoldingModelSupport(myPreviewEditor);
        final EditorSettings settings = myPreviewEditor.getSettings();
        settings.setLineNumbersShown(false);
        settings.setFoldingOutlineShown(true);
        settings.setLineMarkerAreaShown(true);
        settings.setGutterIconsShown(false);
        settings.setAdditionalColumnsCount(0);
        settings.setAdditionalLinesCount(0);
        settings.setLeadingWhitespaceShown(true);
        myPreviewEditor.getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, myPreviewEditor.getColorsScheme().getDefaultBackground());
        myPreviewEditor.getScrollPane().setBorder(IdeBorderFactory.createEmptyBorder());
      }
      if (problemCount == 0) {
        myPreviewEditor.getScrollingModel().scrollTo(myPreviewEditor.offsetToLogicalPosition(selectedElement.getTextOffset()), ScrollType.CENTER_UP);
      }
      myPreviewEditor.getComponent().setBorder(IdeBorderFactory.createEmptyBorder());
      return Pair.create(myPreviewEditor.getComponent(), myPreviewEditor);
    }
    else if (selectedEntity == null) {
      return Pair.create(new InspectionNodeInfo(myTree, myProject), null);
    }
    else if (selectedEntity.isValid()) {
      return Pair.create(InspectionResultsViewUtil.getPreviewIsNotAvailable(selectedEntity), null);
    }
    return Pair.create(InspectionResultsViewUtil.getInvalidEntityLabel(selectedEntity), null);
  }

  private boolean reuseEditorFor(Document document) {
    return myPreviewEditor != null && !myPreviewEditor.isDisposed() && myPreviewEditor.getDocument() == document;
  }

  @NotNull
  public InspectionNode addTool(@NotNull final InspectionToolWrapper toolWrapper,
                                HighlightDisplayLevel errorLevel,
                                boolean groupedBySeverity,
                                boolean isSingleInspectionRun) {
    String groupName =
      toolWrapper.getGroupDisplayName().isEmpty() ? InspectionProfileEntry.GENERAL_GROUP_NAME : toolWrapper.getGroupDisplayName();
    InspectionTreeNode parentNode = getToolParentNode(groupName, toolWrapper.getGroupPath(), errorLevel, groupedBySeverity, isSingleInspectionRun);
    InspectionNode toolNode = new InspectionNode(toolWrapper, myInspectionProfile);
    boolean showStructure = myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE;
    toolNode = myProvider.appendToolNodeContent(myGlobalInspectionContext, toolNode, parentNode, showStructure, groupedBySeverity);
    InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(toolWrapper);
    toolNode = presentation.createToolNode(myGlobalInspectionContext, toolNode, myProvider, parentNode, showStructure, groupedBySeverity);
    ((DefaultInspectionToolPresentation)presentation).setToolNode(toolNode);
    registerActionShortcuts(presentation);
    return toolNode;
  }

  private void registerActionShortcuts(@NotNull InspectionToolPresentation presentation) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final QuickFixAction[] fixes = presentation.getQuickFixes(RefEntity.EMPTY_ELEMENTS_ARRAY, null);
      if (fixes != null) {
        for (QuickFixAction fix : fixes) {
          fix.registerCustomShortcutSet(fix.getShortcutSet(), this);
        }
      }
    });
  }

  @NotNull
  public Set<SuppressIntentionAction> getSuppressActions(InspectionToolWrapper wrapper) {
    return mySuppressActions.computeIfAbsent(wrapper.getShortName(), (w) -> {
      final SuppressIntentionAction[] actions = InspectionManagerEx.getSuppressActions(wrapper);
      return actions == null ? Collections.emptySet() : ContainerUtil.newLinkedHashSet(actions);
    });
  }

  public Set<Object> getSuppressedNodes(String toolId) {
    return mySuppressedNodes.get(toolId);
  }

  @NotNull
  public ExcludedInspectionTreeNodesManager getExcludedManager() {
    return myExcludedInspectionTreeNodesManager;
  }

  @Nullable
  public String getCurrentProfileName() {
    return myInspectionProfile == null ? null : myInspectionProfile.getDisplayName();
  }

  public InspectionProfile getCurrentProfile() {
    return myInspectionProfile;
  }

  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Application app = ApplicationManager.getApplication();
    final Runnable buildAction = () -> {
      try {
        setUpdating(true);
        synchronized (getTreeStructureUpdateLock()) {
          mySeverityGroupNodes.clear();
          myGroups.clear();
          myTree.removeAllNodes();
          addTools(myGlobalInspectionContext.getTools().values());
        }
      }
      finally {
        setUpdating(false);
        UIUtil.invokeLaterIfNeeded(() -> myTree.restoreExpansionAndSelection(false));
      }
    };
    if (app.isUnitTestMode()) {
      buildAction.run();
    } else {
      app.executeOnPooledThread(buildAction);
    }
  }

  public void setUpdating(boolean isUpdating) {
    final Runnable update = () -> {
      if (isUpdating) {
        myUpdatingRequestors++;
      } else {
        myUpdatingRequestors--;
      }
      boolean hasUpdatingRequestors = myUpdatingRequestors > 0;
      myTree.setPaintBusy(hasUpdatingRequestors);
      if (!hasUpdatingRequestors && myLoadingProgressPreview != null) {
        myLoadingProgressPreview.treeLoaded();
      }
      //TODO Dmitrii Batkovich it's a hack (preview panel should be created during selection update)
      if (!hasUpdatingRequestors && mySplitter.getSecondComponent() == null) {
        if (myTree.getSelectionModel().getSelectionPath() == null) {
          TreeUtil.selectFirstNode(myTree);
        }
        syncRightPanel();
      }
    };
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      update.run();
    }
    else {
      app.invokeLater(update, ModalityState.any());
    }
  }

  public Object getTreeStructureUpdateLock() {
    return myTreeStructureUpdateLock;
  }

  public void addTools(Collection<Tools> tools) {
    synchronized (myTreeStructureUpdateLock) {
      InspectionProfileImpl profile = (InspectionProfileImpl)myInspectionProfile;
      boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
      boolean singleInspectionRun = isSingleInspectionRun();
      for (Tools currentTools : tools) {
        InspectionToolWrapper defaultToolWrapper = currentTools.getDefaultState().getTool();
        if (myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS &&
            myExcludedInspectionTreeNodesManager.containsInspectionNode(defaultToolWrapper)) {
          continue;
        }
        final HighlightDisplayKey key = HighlightDisplayKey.find(defaultToolWrapper.getShortName());
        for (ScopeToolState state : myProvider.getTools(currentTools)) {
          InspectionToolWrapper toolWrapper = state.getTool();
          if (ReadAction.compute(() -> myProvider.checkReportedProblems(myGlobalInspectionContext, toolWrapper))) {
            //ReadAction.run(
            //  () ->
                addTool(toolWrapper,
                                         profile.getErrorLevel(key, state.getScope(myProject), myProject),
                                         isGroupedBySeverity,
                                         singleInspectionRun)
            //)
            ;
          }
        }
      }
    }
  }


  @NotNull
  private InspectionTreeNode getToolParentNode(@NotNull String groupName,
                                               @NotNull String[] groupPath,
                                               HighlightDisplayLevel errorLevel,
                                               boolean groupedBySeverity,
                                               boolean isSingleInspectionRun) {
    if (!groupedBySeverity && isSingleInspectionRun) {
      return getTree().getRoot();
    }
    if (groupName.isEmpty()) {
      return getRelativeRootNode(groupedBySeverity, errorLevel);
    }
    ConcurrentMap<String, InspectionGroupNode> map = myGroups.get(errorLevel);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myGroups, errorLevel, ContainerUtil.newConcurrentMap());
    }
    InspectionGroupNode group;
    if (groupedBySeverity) {
      group = map.get(groupName);
    }
    else {
      group = null;
      for (Map<String, InspectionGroupNode> groupMap : myGroups.values()) {
        if ((group = groupMap.get(groupName)) != null) break;
      }
    }
    if (group == null) {
      if (isSingleInspectionRun) {
        return getRelativeRootNode(true, errorLevel);
      }
      group = ConcurrencyUtil.cacheOrGet(map, groupName, new InspectionGroupNode(groupName, groupPath));
      if (!myDisposed) {
        getRelativeRootNode(groupedBySeverity, errorLevel).insertByOrder(group, false);
      }
    }
    return group;
  }

  @NotNull
  private InspectionTreeNode getRelativeRootNode(boolean isGroupedBySeverity, HighlightDisplayLevel level) {
    if (isGroupedBySeverity) {
      InspectionSeverityGroupNode severityGroupNode = mySeverityGroupNodes.get(level);
      if (severityGroupNode == null) {
        InspectionSeverityGroupNode newNode = new InspectionSeverityGroupNode(myProject, level);
        severityGroupNode = ConcurrencyUtil.cacheOrGet(mySeverityGroupNodes, level, newNode);
        if (severityGroupNode == newNode) {
          InspectionTreeNode root = myTree.getRoot();
          root.insertByOrder(severityGroupNode, false);
        }
      }
      return severityGroupNode;
    }
    return myTree.getRoot();
  }

  private OccurenceNavigator getOccurenceNavigator() {
    return myOccurenceNavigator;
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator != null && myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator != null && myOccurenceNavigator.hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.goNextOccurence() : null;
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.goPreviousOccurence() : null;
  }

  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.getNextOccurenceActionName() : "";
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.getPreviousOccurenceActionName() : "";
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return HELP_ID;
    if (DATA_KEY.is(dataId)) return this;
    if (ExclusionHandler.EXCLUSION_HANDLER.is(dataId)) return myExclusionHandler;
    if (myTree == null) return null;
    TreePath[] paths = myTree.getSelectionPaths();

    if (paths == null || paths.length == 0) return null;

    if (paths.length > 1) {
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        return collectPsiElements();
      }
      return null;
    }

    TreePath path = paths[0];

    InspectionTreeNode selectedNode = (InspectionTreeNode)path.getLastPathComponent();

    if (!CommonDataKeys.NAVIGATABLE.is(dataId) && !CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return null;
    }

    if (selectedNode instanceof RefElementNode) {
      final RefElementNode refElementNode = (RefElementNode)selectedNode;
      RefEntity refElement = refElementNode.getElement();
      if (refElement == null) return null;
      final RefEntity item = refElement.getRefManager().getRefinedElement(refElement);

      if (!item.isValid()) return null;

      PsiElement psiElement = item instanceof RefElement ? ((RefElement)item).getElement() : null;
      if (psiElement == null) return null;

      final CommonProblemDescriptor problem = refElementNode.getDescriptor();
      if (problem != null) {
        if (problem instanceof ProblemDescriptor) {
          PsiElement elementFromDescriptor = ((ProblemDescriptor)problem).getPsiElement();
          if (elementFromDescriptor == null) {
            final InspectionTreeNode node = (InspectionTreeNode)refElementNode.getChildAt(0);
            if (node.isValid()) {
              return null;
            }
          } else {
            psiElement = elementFromDescriptor;
          }
        }
        else {
          return null;
        }
      }

      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return getSelectedNavigatable(problem, psiElement);
      }
      else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return psiElement.isValid() ? psiElement : null;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && CommonDataKeys.NAVIGATABLE.is(dataId)) {
      Navigatable navigatable = getSelectedNavigatable(((ProblemDescriptionNode)selectedNode).getDescriptor());
      return navigatable == null ? InspectionResultsViewUtil.getNavigatableForInvalidNode((ProblemDescriptionNode)selectedNode) : navigatable;
    }

    return null;
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor) {
    return getSelectedNavigatable(descriptor,
                                  descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null);
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor, final PsiElement psiElement) {
    if (descriptor instanceof ProblemDescriptorBase) {
      Navigatable navigatable = ((ProblemDescriptorBase)descriptor).getNavigatable();
      if (navigatable != null) {
        return navigatable;
      }
    }
    if (psiElement == null || !psiElement.isValid()) return null;
    PsiFile containingFile = psiElement.getContainingFile();
    VirtualFile virtualFile = containingFile == null ? null : containingFile.getVirtualFile();

    if (virtualFile != null) {
      int startOffset = psiElement.getTextOffset();
      if (descriptor instanceof ProblemDescriptorBase) {
        final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRangeForNavigation();
        if (textRange != null) {
          if (virtualFile instanceof VirtualFileWindow) {
            virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
          }
          startOffset = textRange.getStartOffset();
        }
      }
      return new OpenFileDescriptor(myProject, virtualFile, startOffset);
    }
    return null;
  }

  private PsiElement[] collectPsiElements() {
    RefEntity[] refElements = myTree.getSelectedElements();
    List<PsiElement> psiElements = new ArrayList<>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        psiElements.add(psiElement);
      }
    }

    return PsiUtilCore.toPsiElementArray(psiElements);
  }

  @NotNull
  public InspectionTree getTree() {
    return myTree;
  }

  @NotNull
  public GlobalInspectionContextImpl getGlobalInspectionContext() {
    return myGlobalInspectionContext;
  }

  @NotNull
  public InspectionRVContentProvider getProvider() {
    return myProvider;
  }

  public boolean isSingleToolInSelection() {
    return myTree != null && myTree.getSelectedToolWrapper(true) != null;
  }

  public boolean isRerun() {
    boolean rerun = myRerun;
    myRerun = false;
    return rerun;
  }

  public boolean isSingleInspectionRun() {
    return myInspectionProfile.getSingleTool() != null;
  }

  public static void showPopup(AnActionEvent e, JBPopup popup) {
    final InputEvent event = e.getInputEvent();
    if (event instanceof MouseEvent) {
      popup.showUnderneathOf(event.getComponent());
    }
    else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  @NotNull
  public AnalysisScope getScope() {
    return myScope;
  }

  public boolean isUpdating() {
    return myUpdatingRequestors > 0;
  }

  void updateRightPanelLoading() {
    if (!myDisposed && isUpdating() && myLoadingProgressPreview != null) {
      myLoadingProgressPreview.updateLoadingProgress();
    }
  }

  public boolean hasProblems() {
    return hasProblems(myGlobalInspectionContext.getTools().values(), myGlobalInspectionContext, myProvider);
  }

  public static boolean hasProblems(@NotNull Collection<Tools> tools,
                                    @NotNull GlobalInspectionContextImpl context,
                                    @NotNull InspectionRVContentProvider contentProvider) {
    for (Tools currentTools : tools) {
      for (ScopeToolState state : contentProvider.getTools(currentTools)) {
        InspectionToolWrapper toolWrapper = state.getTool();
        if (contentProvider.checkReportedProblems(context, toolWrapper)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private class CloseAction extends AnAction implements DumbAware {
    private CloseAction() {
      super(CommonBundle.message("action.close"), null, AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myGlobalInspectionContext.close(true);
    }
  }

  public void updateCurrentProfile() {
    final String name = myInspectionProfile.getName();
    myInspectionProfile = (InspectionProfile)myInspectionProfile.getProfileManager().getProfile(name);
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super(InspectionsBundle.message("inspection.action.rerun"), InspectionsBundle.message("inspection.action.rerun"),
            AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(isRerunAvailable());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      rerun();
    }

    private void rerun() {
      InspectionResultsView.this.rerun();
    }
  }

  public boolean isRerunAvailable() {
    return !(myProvider instanceof OfflineInspectionRVContentProvider) && myScope.isValid();
  }

  public void rerun() {
    myRerun = true;
    if (myScope.isValid()) {
      AnalysisUIOptions.getInstance(myProject).save(myGlobalInspectionContext.getUIOptions());
      myGlobalInspectionContext.setTreeState(getTree().getTreeState());
      myGlobalInspectionContext.doInspections(myScope);
    }
  }
}

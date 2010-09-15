/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.commander;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class CommanderPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.commander.CommanderPanel");

  private static final Color DARK_BLUE = new Color(55, 85, 134);
  private static final Color DARK_BLUE_BRIGHTER = new Color(58, 92, 149);
  private static final Color DARK_BLUE_DARKER = new Color(38, 64, 106);

  private Project myProject;
  private AbstractListBuilder myBuilder;
  private JPanel myTitlePanel;
  private JLabel myParentTitle;
  protected final JBList myList;
  private final MyModel myModel;

  private CopyPasteDelegator myCopyPasteDelegator;
  protected final ListSpeedSearch myListSpeedSearch;
  private final IdeView myIdeView = new MyIdeView();
  private final MyDeleteElementProvider myDeleteElementProvider = new MyDeleteElementProvider();
  @NonNls
  private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls
  private static final String ACTION_GO_UP = "GoUp";
  private ProjectAbstractTreeStructureBase myProjectTreeStructure;
  private boolean myActive = true;
  private final List<CommanderHistoryListener> myHistoryListeners = ContainerUtil.createEmptyCOWList();
  private boolean myMoveFocus = false;
  private boolean myEnableSearchHighlighting;

  public CommanderPanel(final Project project, final boolean enablePopupMenu, final boolean enableSearchHighlighting) {
    super(new BorderLayout());
    myProject = project;
    myEnableSearchHighlighting = enableSearchHighlighting;
    myModel = new MyModel();
    myList = new JBList(myModel);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    if (enablePopupMenu) {
      myCopyPasteDelegator = new CopyPasteDelegator(myProject, myList) {
        @NotNull
        protected PsiElement[] getSelectedElements() {
          return CommanderPanel.this.getSelectedElements();
        }
      };
    }

    myListSpeedSearch = new ListSpeedSearch(myList);

    ListScrollingUtil.installActions(myList);

    myList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myBuilder == null) return;
        myBuilder.buildRoot();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);

    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_DRILL_DOWN);
    myList.getInputMap(WHEN_FOCUSED)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK), ACTION_DRILL_DOWN);
    myList.getActionMap().put(ACTION_DRILL_DOWN, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        drillDown();
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          drillDown();
        }
      }
    });
    myList.getInputMap(WHEN_FOCUSED)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK), ACTION_GO_UP);
    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_GO_UP);
    myList.getActionMap().put(ACTION_GO_UP, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        goUp();
      }
    });

    myList.getActionMap().put("selectAll", new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
      }
    });


    if (enablePopupMenu) {
      myList.addMouseListener(new PopupHandler() {
        public void invokePopup(final Component comp, final int x, final int y) {
          CommanderPanel.this.invokePopup(comp, x, y);
        }
      });
    }

    myList.addFocusListener(new FocusAdapter() {
      public void focusGained(final FocusEvent e) {
        setActive(true);
      }

      public void focusLost(final FocusEvent e) {
        setActive(false);
      }
    });

  }

  public boolean isEnableSearchHighlighting() {
    return myEnableSearchHighlighting;
  }

  public ListSpeedSearch getListSpeedSearch() {
    return myListSpeedSearch;
  }

  public void addHistoryListener(CommanderHistoryListener listener) {
    myHistoryListeners.add(listener);
  }

  public void removeHistoryListener(CommanderHistoryListener listener) {
    myHistoryListeners.remove(listener);
  }

  private void updateHistory(boolean elementExpanded) {
    for(CommanderHistoryListener listener: myHistoryListeners) {
      listener.historyChanged(getSelectedElement(), elementExpanded);
    }
  }

  public final JList getList() {
    return myList;
  }

  public final AbstractListBuilder.Model getModel() {
    return myModel;
  }

  public void setMoveFocus(final boolean moveFocus) {
    myMoveFocus = moveFocus;
  }

  public void goUp() {
    if (myBuilder == null) {
      return;
    }
    updateHistory(true);
    myBuilder.goUp();
    updateHistory(false);
  }

  public void drillDown() {
    if (topElementIsSelected()) {
      goUp();
      return;
    }

    if (getSelectedValue() == null) {
      return;
    }

    final AbstractTreeNode element = getSelectedNode();
    if (element.getChildren().size() == 0) {
      if (!shouldDrillDownOnEmptyElement(element)) {
        navigateSelectedElement();
        return;
      }
    }

    if (myBuilder == null) {
      return;
    }
    updateHistory(false);
    myBuilder.drillDown();
    updateHistory(true);
  }

  public boolean navigateSelectedElement() {
    final AbstractTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      if (selectedNode.canNavigateToSource()) {
        selectedNode.navigate(true);
        return true;
      }
    }
    return false;
  }

  protected boolean shouldDrillDownOnEmptyElement(final AbstractTreeNode node) {
    return node instanceof ProjectViewNode && ((ProjectViewNode)node).shouldDrillDownOnEmptyElement();
  }

  private boolean topElementIsSelected() {
    int[] selectedIndices = myList.getSelectedIndices();
    return selectedIndices.length == 1 && selectedIndices[0] == 0 && myModel.getElementAt(selectedIndices[0]) instanceof TopLevelNode;
  }

  public final void setBuilder(final AbstractListBuilder builder) {
    myBuilder = builder;
    removeAll();

    myTitlePanel = new JPanel(new BorderLayout());
    myTitlePanel.setBackground(UIUtil.getControlColor());
    myTitlePanel.setOpaque(true);

    myParentTitle = new MyTitleLabel(myTitlePanel);
    myParentTitle.setText(" ");
    myParentTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myParentTitle.setForeground(Color.black);
    myParentTitle.setUI(new RightAlignedLabelUI());
    final JPanel panel1 = new JPanel(new BorderLayout());
    panel1.setOpaque(false);
    panel1.add(Box.createHorizontalStrut(10), BorderLayout.WEST);
    panel1.add(myParentTitle, BorderLayout.CENTER);
    myTitlePanel.add(panel1, BorderLayout.CENTER);

    add(myTitlePanel, BorderLayout.NORTH);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    scrollPane.setBorder(null);
    scrollPane.getVerticalScrollBar().setFocusable(false); // otherwise the scrollbar steals focus and panel switching with tab is broken 
    scrollPane.getHorizontalScrollBar().setFocusable(false);
    add(scrollPane, BorderLayout.CENTER);

    myBuilder.setParentTitle(myParentTitle);

    // TODO[vova,anton] it seems that the code below performs double focus request. Is it OK?
    myTitlePanel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        myList.requestFocus();
      }

      public void mousePressed(final MouseEvent e) {
        myList.requestFocus();
      }
    });
  }

  public final AbstractListBuilder getBuilder() {
    return myBuilder;
  }

  public final PsiElement getSelectedElement() {
    Object value = getValueAtIndex(getSelectedNode());
    return (PsiElement)(value instanceof PsiElement ? value : null);
  }

  public final PsiElement getSelectedElement(int index) {
    Object elementAtIndex = myModel.getElementAt(index);
    Object value = getValueAtIndex(elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode)elementAtIndex : null);
    return (PsiElement)(value instanceof PsiElement ? value : null);
  }

  public AbstractTreeNode getSelectedNode() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    if (indices.length != 1) return null;
    int index = indices[0];
    if (index >= myModel.getSize()) return null;
    Object elementAtIndex = myModel.getElementAt(index);
    return elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode)elementAtIndex : null;
  }

  private ArrayList<AbstractTreeNode> getSelectedNodes() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (int index : indices) {
      if (index >= myModel.getSize()) continue;
      Object elementAtIndex = myModel.getElementAt(index);
      AbstractTreeNode node = elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode)elementAtIndex : null;
      if (node != null) {
        result.add(node);
      }
    }
    return result;
  }


  public Object getSelectedValue() {
    return getValueAtIndex(getSelectedNode());
  }

  private PsiElement[] getSelectedElements() {
    if (myBuilder == null) return PsiElement.EMPTY_ARRAY;
    final int[] indices = myList.getSelectedIndices();

    final ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
    for (int index : indices) {
      final PsiElement element = getSelectedElement(index);
      if (element != null) {
        elements.add(element);
      }
    }

    return elements.toArray(new PsiElement[elements.size()]);
  }

  private static Object getValueAtIndex(AbstractTreeNode node) {
    if (node == null) return null;
    Object value = node.getValue();
    if (value instanceof StructureViewTreeElement) {
      return ((StructureViewTreeElement)value).getValue();
    }
    return value;
  }

  public final void setActive(final boolean active) {
    myActive = active;
    if (active) {
      myTitlePanel.setBackground(DARK_BLUE);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, DARK_BLUE_BRIGHTER, DARK_BLUE_DARKER));
      myParentTitle.setForeground(Color.white);
    }
    else {
      final Color color = UIUtil.getPanelBackground();
      LOG.assertTrue(color != null);
      myTitlePanel.setBackground(color);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, color.brighter(), color.darker()));
      myParentTitle.setForeground(Color.black);
    }
    final int[] selectedIndices = myList.getSelectedIndices();
    if (selectedIndices.length == 0 && myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
      if (!myList.hasFocus()) {
        myList.requestFocus();
      }
    }
    else if (myList.getModel().getSize() > 0) {
      // need this to generate SelectionChanged events so that listeners, added by Commander, will be notified
      myList.setSelectedIndices(selectedIndices);
    }
  }

  public boolean isActive() {
    return myActive;
  }

  private void invokePopup(final Component c, final int x, final int y) {
    if (myBuilder == null) return;

    if (myList.getSelectedIndices().length <= 1) {
      final int popupIndex = myList.locationToIndex(new Point(x, y));
      if (popupIndex >= 0) {
        myList.setSelectedIndex(popupIndex);
        myList.requestFocus();
      }
    }

    final ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_COMMANDER_POPUP);
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMMANDER_POPUP, group);
    popupMenu.getComponent().show(c, x, y);
  }

  public final void dispose() {
    if (myBuilder != null) {
      myBuilder.dispose();
      myBuilder = null;
    }
    myProject = null;
  }

  public final void setTitlePanelVisible(final boolean flag) {
    myTitlePanel.setVisible(flag);
  }

  public final Object getDataImpl(final String dataId) {
    if (myBuilder == null) return null;
    final Object selectedValue = getSelectedValue();
    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement selectedElement = getSelectedElement();
      return selectedElement != null && selectedElement.isValid() ? selectedElement : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      return filterInvalidElements(getSelectedElements());
    }
    else if (LangDataKeys.PASTE_TARGET_PSI_ELEMENT.is(dataId)) {
      final AbstractTreeNode parentNode = myBuilder.getParentNode();
      final Object element = parentNode != null ? parentNode.getValue() : null;
      return element instanceof PsiElement && ((PsiElement)element).isValid() ? element : null;
    }
    else if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatables();
    }
    else if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getCopyProvider() : null;
    }
    else if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getCutProvider() : null;
    }
    else if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getPasteProvider() : null;
    }
    else if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteElementProvider;
    }
    else if (LangDataKeys.MODULE.is(dataId)) {
      return selectedValue instanceof Module ? selectedValue : null;
    }
    else if (ModuleGroup.ARRAY_DATA_KEY.is(dataId)) {
      return selectedValue instanceof ModuleGroup ? new ModuleGroup[]{(ModuleGroup)selectedValue} : null;
    }
    else if (LibraryGroupElement.ARRAY_DATA_KEY.is(dataId)) {
      return selectedValue instanceof LibraryGroupElement ? new LibraryGroupElement[]{(LibraryGroupElement)selectedValue} : null;
    }
    else if (NamedLibraryElement.ARRAY_DATA_KEY.is(dataId)) {
      return selectedValue instanceof NamedLibraryElement ? new NamedLibraryElement[]{(NamedLibraryElement)selectedValue} : null;
    }

    if (myProjectTreeStructure != null) {
      return myProjectTreeStructure.getDataFromProviders(getSelectedNodes(), dataId);
    }

    return null;
  }

  private Navigatable[] getNavigatables() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    if (indices == null || indices.length == 0) return null;

    final ArrayList<Navigatable> elements = new ArrayList<Navigatable>();
    for (int index : indices) {
      final Object element = myModel.getElementAt(index);
      if (element instanceof AbstractTreeNode) {
        elements.add((Navigatable)element);
      }
    }

    return elements.toArray(new Navigatable[elements.size()]);

  }

  @Nullable
  private static PsiElement[] filterInvalidElements(final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      return null;
    }
    final List<PsiElement> validElements = new ArrayList<PsiElement>(elements.length);
    for (final PsiElement element : elements) {
      if (element.isValid()) {
        validElements.add(element);
      }
    }
    return validElements.size() == elements.length ? elements : validElements.toArray(new PsiElement[validElements.size()]);
  }

  protected final Navigatable createEditSourceDescriptor() {
    return EditSourceUtil.getDescriptor(getSelectedElement());
  }

  public void setProjectTreeStructure(final ProjectAbstractTreeStructureBase projectTreeStructure) {
    myProjectTreeStructure = projectTreeStructure;
  }

  private static final class MyTitleLabel extends JLabel {
    private final JPanel myPanel;

    public MyTitleLabel(final JPanel panel) {
      myPanel = panel;
    }

    public void setText(String text) {
      if (text == null || text.length() == 0) {
        text = " ";
      }
      super.setText(text);
      if (myPanel != null) {
        myPanel.setToolTipText(text.trim().length() == 0 ? null : text);
      }
    }
  }

  private final class MyDeleteElementProvider implements DeleteProvider {
    public void deleteElement(final DataContext dataContext) {
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        final PsiElement[] elements = getSelectedElements();
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    public boolean canDeleteElement(final DataContext dataContext) {
      final PsiElement[] elements = getSelectedElements();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }

  private final class MyIdeView implements IdeView {
    public void selectElement(final PsiElement element) {
      final boolean isDirectory = element instanceof PsiDirectory;
      if (!isDirectory) {
        EditorHelper.openInEditor(element);
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myBuilder.selectElement(element, PsiUtilBase.getVirtualFile(element));
          if (!isDirectory) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (myMoveFocus) {
                  ToolWindowManager.getInstance(myProject).activateEditorComponent();
                }
              }
            });
          }
        }
      }, ModalityState.NON_MODAL);
    }

    private PsiDirectory getDirectory() {
      if (myBuilder == null) return null;
      final Object parentElement = myBuilder.getParentNode();
      if (parentElement instanceof AbstractTreeNode) {
        final AbstractTreeNode parentNode = (AbstractTreeNode)parentElement;
        if (!(parentNode.getValue() instanceof PsiDirectory)) return null;
        return (PsiDirectory)parentNode.getValue();
      }
      else {
        return null;
      }
    }

    public PsiDirectory[] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{directory};
    }

    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }
  
  private static final class MyModel extends AbstractListBuilder.Model {
    final List myElements = new ArrayList();
    public void removeAllElements() {
      int index1 = myElements.size()-1;
      myElements.clear();
      if (index1 >= 0) {
          fireIntervalRemoved(this, 0, index1);
      }
    }

    public void addElement(final Object obj) {
      int index = myElements.size();
      myElements.add(obj);
      fireIntervalAdded(this, index, index);
    }

    public void replaceElements(final List newElements) {
      removeAllElements();
      myElements.addAll(newElements);
      fireIntervalAdded(this, 0, newElements.size());
    }

    public Object[] toArray() {
      return ArrayUtil.toObjectArray(myElements);
    }

    public int indexOf(final Object o) {
      return myElements.indexOf(o);
    }

    public int getSize() {
      return myElements.size();
    }

    public Object getElementAt(final int index) {
      return myElements.get(index);
    }
  }
}

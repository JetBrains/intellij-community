// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.*;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.ide.navbar.actions.NavBarActionHandler;
import com.intellij.ide.navigationToolbar.ui.NavBarUI;
import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.NavBarLocation;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel;
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel.AutoscrollLimit;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.PanelUI;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public class NavBarPanel extends JPanel implements DataProvider, PopupOwner, Disposable, Queryable,
                                                   InfoAndProgressPanel.ScrollableToSelected, NavBarActionHandler {

  private final NavBarModel myModel;

  private final NavBarPresentation myPresentation;
  protected final Project myProject;

  private final ArrayList<NavBarItem> myList = new ArrayList<>();

  private final IdeView myIdeView;
  private FocusListener myNavBarItemFocusListener;

  private LightweightHint myHint = null;
  private NavBarPopup myNodePopup = null;
  private JComponent myHintContainer;
  private Component myContextComponent;

  private final NavBarUpdateQueue myUpdateQueue;
  private final Set<PsiFile> myForcedFileUpdateQueue = new HashSet<>();

  private NavBarItem myContextObject;
  private boolean myDisposed = false;
  private RelativePoint myLocationCache;
  private Selection mySelection = null;
  private AutoscrollLimit myAutoscrollLimit = AutoscrollLimit.UNLIMITED;

  private static class Selection {
    private final int myBarIndex;
    private final @Nullable List<Object> myNodePopupObjects;

    Selection(int barIndex, @Nullable List<Object> nodePopupObjects) {
      myBarIndex = barIndex;
      myNodePopupObjects = nodePopupObjects;
    }
  }

  public NavBarPanel(@NotNull Project project, boolean docked) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myProject = project;
    myModel = createModel();
    myIdeView = new NavBarIdeView(this);
    myPresentation = new NavBarPresentation(myProject);
    myUpdateQueue = new NavBarUpdateQueue(this);

    installPopupHandler(this, -1);
    setOpaque(false);
    if (!ExperimentalUI.isNewUI() && StartupUiUtil.isUnderDarcula() && !docked) {
      setBorder(new LineBorder(Gray._120, 1));
    }
    myUpdateQueue.queueModelUpdateFromFocus();
    myUpdateQueue.queueRebuildUi();

    putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true);
    Disposer.register(project, this);
    AccessibleContextUtil.setName(this, IdeBundle.message("navigation.bar"));
  }

  /**
   * Navigation bar entry point to determine if the keyboard/focus behavior should be
   * compatible with screen readers. This additional level of indirection makes it
   * easier to figure out the various locations in the various navigation bar components
   * that enable screen reader friendly behavior.
   */
  protected boolean allowNavItemsFocus() {
    return ScreenReader.isActive();
  }

  public boolean isFocused() {
    if (allowNavItemsFocus()) {
      return UIUtil.isFocusAncestor(this);
    } else {
      return hasFocus();
    }
  }

  public void addNavBarItemFocusListener(@Nullable FocusListener l) {
    if (l == null) {
      return;
    }
    myNavBarItemFocusListener = AWTEventMulticaster.add(myNavBarItemFocusListener, l);
  }

  public void removeNavBarItemFocusListener(@Nullable FocusListener l) {
    if (l == null) {
      return;
    }
    myNavBarItemFocusListener = AWTEventMulticaster.remove(myNavBarItemFocusListener, l);
  }

  protected void fireNavBarItemFocusGained(final FocusEvent e) {
    FocusListener listener = myNavBarItemFocusListener;
    if (listener != null) {
      listener.focusGained(e);
    }
  }

  protected void fireNavBarItemFocusLost(final FocusEvent e) {
    FocusListener listener = myNavBarItemFocusListener;
    if (listener != null) {
      listener.focusLost(e);
    }
  }

  protected NavBarModel createModel() {
    return new NavBarModel(myProject);
  }

  @Nullable
  public NavBarPopup getNodePopup() {
    return myNodePopup;
  }

  public boolean isNodePopupActive() {
    return myNodePopup != null && myNodePopup.isVisible();
  }

  public LightweightHint getHint() {
    return myHint;
  }

  public NavBarPresentation getPresentation() {
    return myPresentation;
  }

  public void setContextComponent(@Nullable Component contextComponent) {
    myContextComponent = contextComponent;
  }

  public NavBarItem getContextObject() {
    return myContextObject;
  }

  public List<NavBarItem> getItems() {
    return Collections.unmodifiableList(myList);
  }

  public void addItem(NavBarItem item) {
    myList.add(item);
  }

  public void clearItems() {
    final NavBarItem[] toDispose = myList.toArray(new NavBarItem[0]);
    myList.clear();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (NavBarItem item : toDispose) {
        Disposer.dispose(item);
      }
    });

    getNavBarUI().clearItems();
  }

  @Override
  public void setUI(PanelUI ui) {
    getNavBarUI().clearItems();
    super.setUI(ui);
  }

  public NavBarUpdateQueue getUpdateQueue() {
    return myUpdateQueue;
  }

  @Override
  public boolean isNodePopupSpeedSearchActive() {
    return isNodePopupActive() && SpeedSearchSupply.getSupply(myNodePopup.getList()) != null;
  }

  @Override
  public void escape() {
    if (isNodePopupActive()) cancelPopup();
    else {
      myModel.setSelectedIndex(-1);
      hideHint();
      ToolWindowManager.getInstance(myProject).activateEditorComponent();
    }
  }

  @Override
  public void enter() {
    if (isNodePopupActive()) {
      Selection indexes = mySelection;
      List<Object> popupObjects = mySelection == null ? null : mySelection.myNodePopupObjects;
      if (popupObjects != null && !popupObjects.isEmpty()) {
        navigateInsideBar(indexes.myBarIndex, popupObjects.get(0), false);
        return;
      }
    }

    int index = myModel.getSelectedIndex();
    if (index != -1) ctrlClick(index);
  }

  @Override
  public void moveHome() {
    shiftFocus(-myModel.getSelectedIndex());
  }

  @Override
  public void navigate() {
    if (myModel.getSelectedIndex() != -1) {
      doubleClick(myModel.getSelectedIndex());
    }
  }

  @Override
  public void moveUpDown() {
    moveDown();
  }

  public void moveDown() {
    final int index = myModel.getSelectedIndex();
    if (index != -1) {
      if (myModel.size() - 1 == index) {
        shiftFocus(-1);
        ctrlClick(index - 1);
      }
      else {
        ctrlClick(index);
      }
    }
  }

  @Override
  public void moveEnd() {
    shiftFocus(myModel.size() - 1 - myModel.getSelectedIndex());
  }

  public Project getProject() {
    return myProject;
  }

  public NavBarModel getModel() {
    return myModel;
  }

  @Override
  public void dispose() {
    cancelPopup();
    getNavBarUI().clearItems();
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  boolean isSelectedInPopup(Object object) {
    return isNodePopupActive() && myNodePopup.getList().getSelectedValuesList().contains(object);
  }

  static Object expandDirsWithJustOneSubdir(Object target) {
    if (target instanceof PsiElement && !((PsiElement)target).isValid()) return target;
    if (target instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)target;
      for (VirtualFile file = directory.getVirtualFile(), next; ; file = next) {
        VirtualFile[] children = file.getChildren();
        VirtualFile child = children.length == 1 ? children[0] : null;
        //noinspection AssignmentToForLoopParameter
        next = child != null && child.isDirectory() && !child.is(VFileProperty.SYMLINK) ? child : null;
        if (next == null) return ObjectUtils.notNull(directory.getManager().findDirectory(file), directory);
      }
    }
    return target;
  }

  protected void updateItems() {
    for (NavBarItem item : new ArrayList<>(myList)) {
      item.update();
    }
    if (UISettings.getInstance().getShowNavigationBar()) {
      MyNavBarWrapperPanel wrapperPanel = ComponentUtil
        .getParentOfType((Class<? extends MyNavBarWrapperPanel>)MyNavBarWrapperPanel.class,
                         (Component)this);

      if (wrapperPanel != null) {
        wrapperPanel.revalidate();
        wrapperPanel.repaint();
      }
    }
  }

  public void rebuildAndSelectLastDirectoryOrTail(boolean showPopup) {
    rebuildAndSelectItem((list) -> {
      if (UISettings.getInstance().getShowMembersInNavigationBar()) {
        int lastDirectory = ContainerUtil.lastIndexOf(list, (item) -> NavBarPanel.isExpandable(item.getObject()));
        if (lastDirectory >= 0 && lastDirectory < list.size() - 1) return lastDirectory;
      }
      return list.size() - 1;
    }, showPopup);
  }

  public void rebuildAndSelectItem(final Function<? super List<NavBarItem>, Integer> indexToSelectCallback, boolean showPopup) {
    myUpdateQueue.queueModelUpdateFromFocus();
    myUpdateQueue.queueRebuildUi();
    myUpdateQueue.queueSelect(() -> {
      if (!myList.isEmpty()) {
        int index = indexToSelectCallback.apply(myList);
        myModel.setSelectedIndex(index);
        requestSelectedItemFocus();
        if (showPopup) {
          ctrlClick(index);
        }

      }
    });

    myUpdateQueue.flush();
  }

  public void rebuildAndSelectTail(final boolean requestFocus) {
    rebuildAndSelectItem((list) -> list.size() - 1, false);
  }

  public void requestSelectedItemFocus() {
    int index = myModel.getSelectedIndex();
    if (index >= 0 && index < myModel.size() && allowNavItemsFocus()) {
      IdeFocusManager.getInstance(myProject).requestFocus(getItem(index), true);
    } else {
      IdeFocusManager.getInstance(myProject).requestFocus(this, true);
    }
  }

  @Override
  public void moveLeft() {
    move(-1);
  }

  @Override
  public void moveRight() {
    move(1);
  }

  private void move(int direction) {
    boolean withPopup = isNodePopupActive();

    if (withPopup) cancelPopup();
    shiftFocus(direction);
    if (withPopup) restorePopup();
  }

  void shiftFocus(int direction) {
    final int selectedIndex = myModel.getSelectedIndex();
    final int index = myModel.getIndexByModel(selectedIndex + direction);
    myModel.setSelectedIndex(index);
    if (allowNavItemsFocus()) {
      requestSelectedItemFocus();
    }
  }

  @Override
  public void updateAutoscrollLimit(AutoscrollLimit limit) {
    myAutoscrollLimit = limit;
  }

  protected void scrollSelectionToVisible(boolean isOnSelectionChange) {
    if (!isOnSelectionChange
        && UISettings.getInstance().getNavBarLocation() == NavBarLocation.BOTTOM
        && myAutoscrollLimit == AutoscrollLimit.NOT_ALLOWED) {
      return;
    }

    final int selectedIndex = myModel.getSelectedIndex();
    if (selectedIndex == -1 || selectedIndex >= myList.size()) return;
    scrollRectToVisible(myList.get(selectedIndex).getBounds());

    if (myAutoscrollLimit == AutoscrollLimit.ALLOW_ONCE) myAutoscrollLimit = AutoscrollLimit.NOT_ALLOWED;
  }

  @Nullable
  private NavBarItem getItem(int index) {
    if (index != -1 && index < myList.size()) {
      return myList.get(index);
    }
    return null;
  }

  public boolean isInFloatingMode() {
    return myHint != null && myHint.isVisible();
  }


  @Override
  public Dimension getPreferredSize() {
    if (myDisposed || !myList.isEmpty()) {
      return super.getPreferredSize();
    }
    else {
      final NavBarItem item = new NavBarItem(this, null, 0, null);
      final Dimension size = item.getPreferredSize();
      //noinspection deprecation
      ProjectUtilKt.executeOnPooledThread(myProject, () -> Disposer.dispose(item));
      return size;
    }
  }

  public boolean isRebuildUiNeeded() {
    myModel.revalidate();
    if (myList.size() == myModel.size()) {
      int index = 0;
      for (NavBarItem eachLabel : myList) {
        Object eachElement = myModel.get(index);
        if (eachLabel.getObject() == null || !eachLabel.getObject().equals(eachElement)) {
          return true;
        }

        if (eachLabel.getObject() instanceof PsiFile && myForcedFileUpdateQueue.remove(eachLabel.getObject())) {
          return true;
        }

        if (!StringUtil.equals(eachLabel.getText(), getPresentation().getPresentableText(eachElement, false))) {
          return true;
        }

        if (!Objects.equals(eachLabel.getIcon(), getPresentation().getIcon(eachElement))) {
          return true;
        }

        SimpleTextAttributes modelAttributes1 = myPresentation.getTextAttributes(eachElement, true);
        SimpleTextAttributes modelAttributes2 = myPresentation.getTextAttributes(eachElement, false);
        SimpleTextAttributes labelAttributes = eachLabel.getAttributes();

        if (!modelAttributes1.toTextAttributes().equals(labelAttributes.toTextAttributes())
            && !modelAttributes2.toTextAttributes().equals(labelAttributes.toTextAttributes())) {
          return true;
        }
        index++;
      }
      return false;
    }
    else {
      return true;
    }
  }

  void installPopupHandler(@NotNull JComponent component, int index) {
    NavBarPanel navBarPanel = this;
    component.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionGroup actionGroup = new ActionGroup() {
          @Override
          public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            if (e == null) return EMPTY_ARRAY;
            String popupGroupId = null;
            for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
              popupGroupId = modelExtension.getPopupMenuGroup(NavBarPanel.this);
              if (popupGroupId != null) break;
            }
            if (popupGroupId == null) popupGroupId = IdeActions.GROUP_NAVBAR_POPUP;
            ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(popupGroupId);
            return group == null ? EMPTY_ARRAY : group.getChildren(e);
          }
        };
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.NAVIGATION_BAR_POPUP, actionGroup);
        popupMenu.setTargetComponent(navBarPanel);
        JPopupMenu menu = popupMenu.getComponent();

        if (index != -1 && !navBarPanel.isNodePopupActive()) {
          myModel.setSelectedIndex(index);
        }

        menu.show(isNodePopupActive() ? myNodePopup.getComponent() : navBarPanel,
                  component.getX() + x,
                  component.getY() + y);
      }
    });
  }

  public void installActions(int index, NavBarItem component) {
    //suppress it for a while
    //installDnD(index, component);
    installPopupHandler(component, index);
    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (SystemInfo.isWindows) {
          click(e);
        }
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        if (!SystemInfo.isWindows) {
          click(e);
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (e.isConsumed() || !ExperimentalUI.isNewUI()) return;
        NavBarItem item = getItem(index);
        if (item != null) {
          item.setMouseHover(true);
          repaint();
        }
        e.consume();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (e.isConsumed() || !ExperimentalUI.isNewUI()) return;
        NavBarItem item = getItem(index);
        if (item != null) {
          item.setMouseHover(false);
          repaint();
        }
        e.consume();
      }

      private void click(final MouseEvent e) {
        if (e.isConsumed()) return;

        if (e.isPopupTrigger()) return;
        if (e.getClickCount() == 1) {
          ctrlClick(index);
          e.consume();
        }
        else if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          requestSelectedItemFocus();
          doubleClick(index);
          e.consume();
        }
      }
    });

    ListenerUtil.addKeyListener(component, new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
          ctrlClick(index);
          myModel.setSelectedIndex(index);
          e.consume();
        }
      }
    });
  }

  private void installDnD(final int index, NavBarItem component) {
    DnDSupport.createBuilder(component)
      .setBeanProvider(dnDActionInfo -> new DnDDragStartBean(new TransferableWrapper() {
        @Override
        public List<File> asFileList() {
          Object o = myModel.get(index);
          if (o instanceof PsiElement) {
            VirtualFile vf = o instanceof PsiDirectory ? ((PsiDirectory)o).getVirtualFile()
                                                       : ((PsiElement)o).getContainingFile().getVirtualFile();
            if (vf != null) {
              return Collections.singletonList(new File(vf.getPath()).getAbsoluteFile());
            }
          }
          return Collections.emptyList();
        }

        @Override
        public TreeNode[] getTreeNodes() {
          return null;
        }

        @Override
        public PsiElement[] getPsiElements() {
          return null;
        }
      }))
      .setDisposableParent(component)
      .install();
  }

  private void doubleClick(final int index) {
    doubleClick(myModel.getElement(index));
  }

  protected void doubleClick(final Object object) {
    Object target = ObjectUtils.chooseNotNull(getNavigatable(object), object);
    if (target instanceof Navigatable) {
      Navigatable navigatable = (Navigatable)target;
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
      }
    }
    else if (target instanceof Module) {
      ProjectView projectView = ProjectView.getInstance(myProject);
      AbstractProjectViewPane projectViewPane = projectView.getProjectViewPaneById(projectView.getCurrentViewId());
      if (projectViewPane != null) {
        projectViewPane.selectModule((Module)target, true);
      }
    }
    else if (target instanceof Project) {
      return;
    }
    hideHint(true);
  }

  @Nullable
  private Navigatable getNavigatable(Object object) {
    return (Navigatable)getSlowData(CommonDataKeys.NAVIGATABLE.getName(), myProject, JBIterable.of(object));
  }

  private void ctrlClick(final int index) {
    if (isNodePopupActive()) {
      cancelPopup();
      if (myModel.getSelectedIndex() == index) {
        return;
      }
    }

    final Object object = myModel.getElement(index);
    final List<Object> objects = myModel.getChildren(object);

    if (!objects.isEmpty()) {
      final Object[] siblings = new Object[objects.size()];
      for (int i = 0; i < objects.size(); i++) {
        siblings[i] = objects.get(i);
      }
      final NavBarItem item = getItem(index);

      final int selectedIndex = index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0;
      myNodePopup = new NavBarPopup(this, index, siblings, index, selectedIndex);
      myModel.setSelectedIndex(index);
      myNodePopup.show(item);
      item.update();
    }
  }

  @Nullable NavBarItem getItemWithObject(Object object) {
    for (NavBarItem item: myList) {
      if (item.getObject() == object) return item;
    }

    return null;
  }

  protected void navigateInsideBar(int sourceItemIndex, final Object object, boolean forceNavigate) {
    UIEventLogger.NavBarNavigate.log(myProject);

    boolean restorePopup = !forceNavigate && shouldRestorePopupOnSelect(object, sourceItemIndex);
    Object obj = expandDirsWithJustOneSubdir(object);
    myContextObject = null;

    myUpdateQueue.cancelAllUpdates();
    if (myNodePopup != null && myNodePopup.isVisible()) {
      myUpdateQueue.queueModelUpdateForObject(obj);
    }
    myUpdateQueue.queueRebuildUi();

    myUpdateQueue.queueAfterAll(() -> {
      int index = myModel.indexOf(obj);
      if (index >= 0) {
        myModel.setSelectedIndex(index);
      }

      if (myModel.hasChildren(obj) && restorePopup) {
        restorePopup();
      }
      else {
        doubleClick(obj);
      }
    }, NavBarUpdateQueue.ID.NAVIGATE_INSIDE);
  }

  private boolean shouldRestorePopupOnSelect(Object obj, int sourceItemIndex) {
    if (sourceItemIndex < myModel.size() - 1 && myModel.get(sourceItemIndex+1) == obj) return true;
    return isExpandable(obj);
  }

  public static boolean isExpandable(Object obj) {
    if (!(obj instanceof PsiElement)) return true;
    PsiElement psiElement = (PsiElement)obj;
    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      Boolean expand = modelExtension.shouldExpandOnClick(psiElement);
      if (expand != null) return expand;
    }
    return psiElement instanceof PsiDirectory || psiElement instanceof PsiDirectoryContainer;
  }

  void restorePopup() {
    cancelPopup();
    ctrlClick(myModel.getSelectedIndex());
  }

  void cancelPopup() {
    cancelPopup(false);
  }


  void cancelPopup(boolean ok) {
    if (myNodePopup != null) {
      myNodePopup.hide(ok);
      myNodePopup = null;
      if (allowNavItemsFocus()) {
        requestSelectedItemFocus();
      }
    }
  }

  void hideHint() {
    hideHint(false);
  }

  protected void hideHint(boolean ok) {
    cancelPopup(ok);
    if (myHint != null) {
      myHint.hide(ok);
      myHint = null;
    }
  }

  @Override
  @Nullable
  public Object getData(@NotNull String dataId) {
    return getDataImpl(dataId, this, this::getSelection);
  }

  @NotNull
  JBIterable<?> getSelection() {
    Object barObject = null;
    List<Object> popupObjects = null;

    if (mySelection != null) {
      barObject = myModel.getRawElement(mySelection.myBarIndex);
      popupObjects = mySelection.myNodePopupObjects;
    }

    if (barObject != null) {
      if (popupObjects == null) {
        return JBIterable.of(barObject).filterMap(myModel::unwrapRaw);
      }

      if (!popupObjects.isEmpty()) {
        return JBIterable.from(popupObjects).filterMap(myModel::unwrapRaw);
      }
    }

    Object selectedObject = myModel.getRawSelectedObject();
    if (selectedObject == null) return JBIterable.empty();
    return JBIterable.of(selectedObject).filterMap(myModel::unwrapRaw);
  }

  void updatePopupSelection(List<Object> objects) {
    mySelection = new Selection(myModel.getSelectedIndex(), objects);
  }

  void updateSelection() {
    mySelection = new Selection(myModel.getSelectedIndex(), null);
  }

  @Nullable Object getDataImpl(@NotNull String dataId, @NotNull JComponent source, @NotNull Supplier<? extends JBIterable<?>> selection) {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) {
      return this; // see NavBarActions#update
    }
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return !myProject.isDisposed() ? myProject : null;
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      JBIterable<?> finalSelection = selection.get();
      return (DataProvider)slowId -> getSlowData(slowId, myProject, finalSelection);
    }
    if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
    }
    // fast extension data without selection (allows to override cut/copy/paste providers)
    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      Object data = modelExtension.getData(dataId, o -> null);
      if (data != null) return data;
    }
    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return getCopyPasteDelegator(source).getCutProvider();
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return getCopyPasteDelegator(source).getCopyProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return getCopyPasteDelegator(source).getPasteProvider();
    }
    return null;
  }

  public static @Nullable Object getSlowData(@NotNull String dataId, @NotNull Project project, @NotNull JBIterable<?> selection) {
    DataProvider provider = o -> getSlowDataImpl(o, project, selection);
    // slow extension data with selection
    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      Object data = modelExtension.getData(dataId, provider);
      if (data != null) return data;
    }
    return provider.getData(dataId);
  }

  private static @Nullable Object getSlowDataImpl(@NotNull String dataId, @NotNull Project project, @NotNull JBIterable<?> selection) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return !project.isDisposed() ? project : null;
    }
    if (PlatformCoreDataKeys.MODULE.is(dataId)) {
      Module module = selection.filter(Module.class).first();
      if (module != null && !module.isDisposed()) return module;
      PsiElement element = selection.filter(PsiElement.class).first();
      if (element != null) {
        return ModuleUtilCore.findModuleForPsiElement(element);
      }
      return null;
    }
    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      PsiDirectory directory = selection.filter(PsiDirectory.class).first();
      if (directory != null) {
        VirtualFile dir = directory.getVirtualFile();
        if (ProjectRootsUtil.isModuleContentRoot(dir, project)) {
          return ModuleUtilCore.findModuleForPsiElement(directory);
        }
      }
      return null;
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      PsiElement element = selection.filter(PsiElement.class).first();
      return element != null && element.isValid() ? element : null;
    }
    if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      List<PsiElement> result = selection.filter(PsiElement.class)
        .filter(e -> e != null && e.isValid()).toList();
      return result.isEmpty() ? null : result.toArray(PsiElement.EMPTY_ARRAY);
    }
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      Set<VirtualFile> files = selection.filter(PsiElement.class)
        .filter(e -> e != null && e.isValid())
        .filterMap(e -> PsiUtilCore.getVirtualFile(e)).toSet();
      return !files.isEmpty() ? VfsUtilCore.toVirtualFileArray(files) : null;
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      List<Navigatable> elements = selection.filter(Navigatable.class).toList();
      return elements.isEmpty() ? null : elements.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return selection.filter(Module.class).isNotEmpty()
             ? ModuleDeleteProvider.getInstance()
             : new DeleteHandler.DefaultDeleteProvider();
    }
    return null;
  }

  @NotNull
  private CopyPasteSupport getCopyPasteDelegator(@NotNull JComponent source) {
    String key = "NavBarPanel.copyPasteDelegator";
    Object result = source.getClientProperty(key);
    if (!(result instanceof CopyPasteSupport)) {
      source.putClientProperty(key, result = new CopyPasteDelegator(myProject, source));
    }
    return (CopyPasteSupport)result;
  }

  @Override
  public Point getBestPopupPosition() {
    int index = myModel.getSelectedIndex();
    final int modelSize = myModel.size();
    if (index == -1) {
      index = modelSize - 1;
    }
    if (index > -1 && index < modelSize) {
      final NavBarItem item = getItem(index);
      if (item != null) {
        return new Point(item.getX(), item.getY() + item.getHeight());
      }
    }
    return null;
  }

  @Override
  public @Nullable JComponent getPopupComponent() {
    return isNodePopupActive() ? myNodePopup.getList() : null;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!isDisposed()) {
      Disposable disposable = NavBarListener.subscribeTo(this);
      Disposer.register(this, disposable);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (isDisposeOnRemove() && ScreenUtil.isStandardAddRemoveNotify(this)) {
      Disposer.dispose(this);
    }
  }

  protected boolean isDisposeOnRemove() {
    return true;
  }

  public void updateState(final boolean show) {
    if (show) {
      myUpdateQueue.queueModelUpdateFromFocus();
      myUpdateQueue.queueRebuildUi();
    }
  }

  // ------ popup NavBar ----------
  public void showHint(@Nullable final Editor editor, final DataContext dataContext) {
    myModel.updateModelAsync(dataContext, () -> {
      if (myModel.isEmpty()) return;
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(this);
      panel.setOpaque(true);

      if (ExperimentalUI.isNewUI()) {
        panel.setBorder(new JBEmptyBorder(JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets()));
        panel.setBackground(JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_BACKGROUND);
      }
      else {
        panel.setBackground(UIUtil.getListBackground());
      }

      myHint = new LightweightHint(panel) {
        @Override
        public void hide() {
          super.hide();
          cancelPopup();
          Disposer.dispose(NavBarPanel.this);
        }
      };
      myHint.setForceShowAsPopup(true);
      myHint.setFocusRequestor(this);
      final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      myUpdateQueue.rebuildUi();
      if (editor == null) {
        myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
        getHintContainerShowPoint().doWhenDone((Consumer<RelativePoint>)relativePoint -> {
          final Component owner = focusManager.getFocusOwner();
          final Component cmp = relativePoint.getComponent();
          if (cmp instanceof JComponent && cmp.isShowing()) {
            myHint.show((JComponent)cmp, relativePoint.getPoint().x, relativePoint.getPoint().y,
                        owner instanceof JComponent ? (JComponent)owner : null,
                        new HintHint(relativePoint.getComponent(), relativePoint.getPoint()));
          }
        });
      }
      else {
        myHintContainer = editor.getContentComponent();
        getHintContainerShowPoint().doWhenDone((Consumer<RelativePoint>)rp -> {
          Point p = rp.getPointOn(myHintContainer).getPoint();
          final HintHint hintInfo = new HintHint(editor, p);
          HintManagerImpl.getInstanceImpl().showEditorHint(myHint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true, hintInfo);
        });
      }

      rebuildAndSelectLastDirectoryOrTail(true);
    });
  }

  AsyncResult<RelativePoint> getHintContainerShowPoint() {
    AsyncResult<RelativePoint> result = new AsyncResult<>();
    if (myLocationCache == null) {
      if (myHintContainer != null) {
        final Point p = AbstractPopup.getCenterOf(myHintContainer, this);
        p.y -= myHintContainer.getVisibleRect().height / 4;
        myLocationCache = RelativePoint.fromScreen(p);
      }
      else {
        DataManager dataManager = DataManager.getInstance();
        if (myContextComponent != null) {
          DataContext ctx = dataManager.getDataContext(myContextComponent);
          myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
        }
        else {
          dataManager.getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> {
            myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
            DataContext ctx = dataManager.getDataContext(myContextComponent);
            myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
          });
        }
      }
    }
    final Component c = myLocationCache.getComponent();
    if (!(c instanceof JComponent && c.isShowing())) {
      //Yes. It happens sometimes.
      // 1. Empty frame. call nav bar, select some package and open it in Project View
      // 2. Call nav bar, then Esc
      // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
      // 4. Call nav bar. NPE. ta da
      final JComponent ideFrame = WindowManager.getInstance().getIdeFrame(getProject()).getComponent();
      final JRootPane rootPane = UIUtil.getRootPane(ideFrame);
      myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(rootPane);
    }
    result.setDone(myLocationCache);
    return result;
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < myList.size(); i++) {
      NavBarItem each = myList.get(i);
      if (each.isSelected()) {
        result.append("[").append(each.getText()).append("]");
      }
      else {
        result.append(each.getText());
      }
      if (i < myList.size() - 1) {
        result.append(">");
      }
    }
    info.put("navBar", result.toString());

    if (isNodePopupActive()) {
      StringBuilder popupText = new StringBuilder();
      JBList list = myNodePopup.getList();
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object eachElement = list.getModel().getElementAt(i);
        String text = new NavBarItem(this, eachElement, myNodePopup, true).getText();
        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex != -1 && eachElement.equals(list.getSelectedValue())) {
          popupText.append("[").append(text).append("]");
        }
        else {
          popupText.append(text);
        }
        if (i < list.getModel().getSize() - 1) {
          popupText.append(">");
        }
      }
      info.put("navBarPopup", popupText.toString());
    }
  }

  @NotNull
  public NavBarUI getNavBarUI() {
    return NavBarUIManager.getUI();
  }

  void queueFileUpdate(PsiFile psiFile) {
    myForcedFileUpdateQueue.add(psiFile);
  }
}
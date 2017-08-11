/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeView;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.navigationToolbar.ui.NavBarUI;
import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
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
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.PanelUI;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 */
public class NavBarPanel extends JPanel implements DataProvider, PopupOwner, Disposable, Queryable {

  private final NavBarModel myModel;

  private final NavBarPresentation myPresentation;
  private final Project myProject;

  private final ArrayList<NavBarItem> myList = new ArrayList<>();

  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private final IdeView myIdeView;
  private final CopyPasteDelegator myCopyPasteDelegator;
  private LightweightHint myHint = null;
  private NavBarPopup myNodePopup = null;
  private JComponent myHintContainer;
  private Component myContextComponent;

  private final NavBarUpdateQueue myUpdateQueue;

  private NavBarItem myContextObject;
  private boolean myDisposed = false;
  private RelativePoint myLocationCache;

  public NavBarPanel(@NotNull Project project, boolean docked) {
    super(new FlowLayout(FlowLayout.LEFT, 0 , 0));
    myProject = project;
    myModel = createModel();
    myIdeView = new NavBarIdeView(this);
    myPresentation = new NavBarPresentation(myProject);
    myUpdateQueue = new NavBarUpdateQueue(this);

    CustomizationUtil.installPopupHandler(this, IdeActions.GROUP_NAVBAR_POPUP, ActionPlaces.NAVIGATION_BAR_POPUP);
    setOpaque(false);
    if (!docked && UIUtil.isUnderDarcula()) {
      setBorder(new LineBorder(Gray._120, 1));
    }
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, NavBarPanel.this) {
      @Override
      @NotNull
      protected PsiElement[] getSelectedElements() {
        final PsiElement element = getSelectedElement(PsiElement.class);
        return element == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{element};
      }
    };

    myUpdateQueue.queueModelUpdateFromFocus();
    myUpdateQueue.queueRebuildUi();
    if (!docked) {
      final ActionCallback typeAheadDone = new ActionCallback();
      IdeFocusManager.getInstance(project).typeAheadUntil(typeAheadDone);
      myUpdateQueue.queueTypeAheadDone(typeAheadDone);
    }

    Disposer.register(project, this);
  }

  protected NavBarModel createModel() {
    return new NavBarModel(myProject);
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
    final NavBarItem[] toDispose = myList.toArray(new NavBarItem[myList.size()]);
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

  public void escape() {
    myModel.setSelectedIndex(-1);
    hideHint();
    ToolWindowManager.getInstance(myProject).activateEditorComponent();
  }

  public void enter() {
    navigateInsideBar(myModel.getSelectedValue());
  }

  public void moveHome() {
    shiftFocus(-myModel.getSelectedIndex());
  }

  public void navigate() {
    if (myModel.getSelectedIndex() != -1) {
      doubleClick(myModel.getSelectedIndex());
    }
  }

  public void moveDown() {
    final int index = myModel.getSelectedIndex();
    if (index != -1) {
      if (myModel.size() - 1 == index) {
        shiftFocus(-1);
        ctrlClick(index - 1);
      } else {
        ctrlClick(index);
      }
    }
  }

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
    NavBarListener.unsubscribeFrom(this);
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  boolean isSelectedInPopup(Object object) {
    return isNodePopupActive() && Arrays.asList(myNodePopup.getSelectedValues()).contains(object);
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

  void updateItems() {
    for (NavBarItem item : myList) {
      item.update();
    }
    if (UISettings.getInstance().getShowNavigationBar()) {
      NavBarRootPaneExtension.NavBarWrapperPanel wrapperPanel = (NavBarRootPaneExtension.NavBarWrapperPanel)
        SwingUtilities.getAncestorOfClass(NavBarRootPaneExtension.NavBarWrapperPanel.class, this);

      if (wrapperPanel != null) {
        wrapperPanel.revalidate();
        wrapperPanel.repaint();
      }
    }
  }

  void resetSelection() {
    int size = myModel.size();
    if (size > 0) myModel.setSelectedIndex(size - 1);
  }

  public void rebuildAndSelectTail(final boolean requestFocus) {
    myUpdateQueue.queueModelUpdateFromFocus();
    myUpdateQueue.queueRebuildUi();
    myUpdateQueue.queueSelect(() -> {
      if (!myList.isEmpty()) {
        myModel.setSelectedIndex(myList.size() - 1);
        if (requestFocus) {
          IdeFocusManager.getInstance(myProject).requestFocus(this, true);
        }
      }
    });

    myUpdateQueue.flush();
  }

  public void moveLeft() {
    shiftFocus(-1);
  }

  public void moveRight() {
    shiftFocus(1);
  }
  void shiftFocus(int direction) {
    final int selectedIndex = myModel.getSelectedIndex();
    final int index = myModel.getIndexByModel(selectedIndex + direction);
    myModel.setSelectedIndex(index);
  }

  void scrollSelectionToVisible() {
    final int selectedIndex = myModel.getSelectedIndex();
    if (selectedIndex == -1 || selectedIndex >= myList.size()) return;
    scrollRectToVisible(myList.get(selectedIndex).getBounds());
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
    if (!myList.isEmpty()) {
      return super.getPreferredSize();
    }
    else {
      final NavBarItem item = new NavBarItem(this, null, 0, null);
      final Dimension size = item.getPreferredSize();
      ApplicationManager.getApplication().executeOnPooledThread(() -> Disposer.dispose(item));
      return size;
    }
  }

  boolean isRebuildUiNeeded() {
    myModel.revalidate();
    if (myList.size() == myModel.size()) {
      int index = 0;
      for (NavBarItem eachLabel : myList) {
        Object eachElement = myModel.get(index);
        if (eachLabel.getObject() == null || !eachLabel.getObject().equals(eachElement)) {
          return true;
        }

        if (!StringUtil.equals(eachLabel.getText(), getPresentation().getPresentableText(eachElement))) {
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
    } else {
      return true;
    }
  }


  @Nullable
  Window getWindow() {
    return !isShowing() ? null : (Window)UIUtil.findUltimateParent(this);
  }

  public void installActions(final int index, final NavBarItem component) {
    //suppress it for a while
    //installDnD(index, component);

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

      private void click(final MouseEvent e) {
        if (e.isConsumed()) return;

        if (e.isPopupTrigger()) {
          myModel.setSelectedIndex(index);
          IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
          rightClick(index);
          e.consume();
        }
        else if (!e.isPopupTrigger()) {
          if (e.getClickCount() == 1) {
            ctrlClick(index);
            myModel.setSelectedIndex(index);
            e.consume();
          }
          else if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            myModel.setSelectedIndex(index);
            IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
            doubleClick(index);
            e.consume();
          }
        }
      }
    });
  }

  private void installDnD(final int index, NavBarItem component) {
    DnDSupport.createBuilder(component)
      .setBeanProvider(dnDActionInfo -> new DnDDragStartBean(new TransferableWrapper() {
        @Override
        public List<File> asFileList() {
          final Object o = myModel.get(index);
          if (o instanceof PsiElement) {
            final VirtualFile vf =  o instanceof PsiDirectory ? ((PsiDirectory)o).getVirtualFile()
                                                              : ((PsiElement)o).getContainingFile().getVirtualFile();
            if (vf != null) {
              return Arrays.asList(new File(vf.getPath()).getAbsoluteFile());
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

  private void doubleClick(final Object object) {
    if (object instanceof Navigatable) {
      Navigatable navigatable = (Navigatable)object;
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
      }
    }
    else if (object instanceof Module) {
      ProjectView projectView = ProjectView.getInstance(myProject);
      AbstractProjectViewPane projectViewPane = projectView.getProjectViewPaneById(projectView.getCurrentViewId());
      if (projectViewPane != null) {
        projectViewPane.selectModule((Module)object, true);
      }
    }
    else if (object instanceof Project) {
      return;
    }
    hideHint(true);
  }

  private void ctrlClick(final int index) {
    if (isNodePopupShowing()) {
      cancelPopup();
      if (myModel.getSelectedIndex() == index) {
        return;
      }
    }

    final Object object = myModel.getElement(index);
    final List<Object> objects = myModel.getChildren(object);

    if (!objects.isEmpty()) {
      final Object[] siblings = new Object[objects.size()];
      //final Icon[] icons = new Icon[objects.size()];
      for (int i = 0; i < objects.size(); i++) {
        siblings[i] = objects.get(i);
        //icons[i] = NavBarPresentation.getIcon(siblings[i], false);
      }
      final NavBarItem item = getItem(index);

      final int selectedIndex = index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0;
      myNodePopup = new NavBarPopup(this, siblings, selectedIndex);
      if (item != null && item.isShowing()) {
        myNodePopup.show(item);
        item.update();
      }
    }
  }

  boolean isNodePopupShowing() {
    return myNodePopup != null && myNodePopup.isVisible();
  }

  protected void navigateInsideBar(final Object object) {
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

      if (myModel.hasChildren(obj)) {
        restorePopup();
      }
      else {
        doubleClick(obj);
      }
    }, NavBarUpdateQueue.ID.NAVIGATE_INSIDE);
  }

  void rightClick(final int index) {
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_POPUP);
    final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.NAVIGATION_BAR_POPUP, group);
    final NavBarItem item = getItem(index);
    if (item != null) {
      popupMenu.getComponent().addPopupMenuListener(new PopupMenuListenerAdapter() {
        @Override
        public void popupMenuCanceled(PopupMenuEvent event) {
          resetSelection(); // select last item if popup cancelled
        }
      });
      popupMenu.getComponent().show(this, item.getX(), item.getY() + item.getHeight());
    }
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
    }
  }

  void hideHint() {
    hideHint(false);
  }

  void hideHint(boolean ok) {
    cancelPopup(ok);
    if (myHint != null) {
      myHint.hide(ok);
      myHint = null;
    }
  }

  @Override
  @Nullable
  public Object getData(String dataId) {
    // First of all try to obtain data from extension
    for (NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      Object data = modelExtension.getData(dataId, this);
      if (data != null) return data;
    }

    if (CommonDataKeys.PROJECT.is(dataId)) {
      return !myProject.isDisposed() ? myProject : null;
    }
    if (LangDataKeys.MODULE.is(dataId)) {
      final Module module = getSelectedElement(Module.class);
      if (module != null && !module.isDisposed()) return module;
      final PsiElement element = getSelectedElement(PsiElement.class);
      if (element != null) {
        return ModuleUtilCore.findModuleForPsiElement(element);
      }
      return null;
    }
    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      final PsiDirectory directory = getSelectedElement(PsiDirectory.class);
      if (directory != null) {
        final VirtualFile dir = directory.getVirtualFile();
        if (ProjectRootsUtil.isModuleContentRoot(dir, myProject)) {
          return ModuleUtilCore.findModuleForPsiElement(directory);
        }
      }
      return null;
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement element = getSelectedElement(PsiElement.class);
      return element != null && element.isValid() ? element : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final List<PsiElement> elements = getSelectedElements(PsiElement.class);
      if (elements == null || elements.isEmpty()) return null;
      List<PsiElement> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element != null && element.isValid()) {
          result.add(element);
        }
      }
      return result.isEmpty() ? null : result.toArray(new PsiElement[result.size()]);
    }

    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      PsiElement[] psiElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
      if (psiElements == null) return null;
      Set<VirtualFile> files = ContainerUtil.newLinkedHashSet();
      for (PsiElement element : psiElements) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
        ContainerUtil.addIfNotNull(files, virtualFile);
      }
      return !files.isEmpty() ? VfsUtilCore.toVirtualFileArray(files) : null;
    }
    
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      final List<Navigatable> elements = getSelectedElements(Navigatable.class);
      return elements == null || elements.isEmpty() ? null : elements.toArray(new Navigatable[elements.size()]);
    }

    if (PlatformDataKeys.CONTEXT_COMPONENT.is(dataId)) {
      return this;
    }
    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return getSelectedElement(Module.class) != null ? myDeleteModuleProvider : new DeleteHandler.DefaultDeleteProvider();
    }

    if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
    }

    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  <T> T getSelectedElement(Class<T> klass) {
    Object value = null;
    if (myNodePopup != null) {
      value = myNodePopup.getSelectedValue();
    }
    if (value == null) value =  myModel.getSelectedValue();
    if (value == null) {
      final int modelSize = myModel.size();
      if (modelSize > 0) {
        value = myModel.getElement(modelSize - 1);
      }
    }
    return value != null && klass.isAssignableFrom(value.getClass()) ? (T)value : null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  <T> List<T> getSelectedElements(Class<T> klass) {
    Object[] values = null;
    if (myNodePopup != null) {
      values = myNodePopup.getSelectedValues();
    }
    if (values == null) {
      final T selectedElement = getSelectedElement(klass);
      return selectedElement == null ? null : Arrays.asList(selectedElement);
    } else {
      List<T> result = new ArrayList<>();
      for (Object value : values) {
        if (value != null && klass.isAssignableFrom(value.getClass())) {
          result.add((T)value);
        }
      }
      return result;
    }
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
  public void addNotify() {
    super.addNotify();
    NavBarListener.subscribeTo(this);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this))
      Disposer.dispose(this);
  }

  public void updateState(final boolean show) {
    if (show) {
      myUpdateQueue.queueModelUpdateFromFocus();
      myUpdateQueue.queueRebuildUi();
    }
  }

  // ------ popup NavBar ----------
  public void showHint(@Nullable final Editor editor, final DataContext dataContext) {
    myModel.updateModel(dataContext);
    if (myModel.isEmpty()) return;
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(this);
    panel.setOpaque(true);
    panel.setBackground(UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());

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
      myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
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

    rebuildAndSelectTail(true);
  }

  AsyncResult<RelativePoint> getHintContainerShowPoint() {
    final AsyncResult<RelativePoint> result = new AsyncResult<>();
    if (myLocationCache == null) {
      if (myHintContainer != null) {
        final Point p = AbstractPopup.getCenterOf(myHintContainer, this);
        p.y -= myHintContainer.getVisibleRect().height / 4;
        myLocationCache = RelativePoint.fromScreen(p);
      } else {
        if (myContextComponent != null) {
          myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(DataManager.getInstance().getDataContext(myContextComponent));
        } else {
          DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> {
            myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
            myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(DataManager.getInstance().getDataContext(myContextComponent));
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
  public void putInfo(@NotNull Map<String, String> info) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < myList.size(); i++) {
      NavBarItem each = myList.get(i);
      if (each.isSelected()) {
        result.append("[" + each.getText() + "]");
      } else {
        result.append(each.getText());
      }
      if (i < myList.size() - 1) {
        result.append(">");
      }
    }
    info.put("navBar", result.toString());
    
    if (isNodePopupShowing()) {
      StringBuilder popupText = new StringBuilder();
      JBList list = myNodePopup.getList();
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object eachElement = list.getModel().getElementAt(i);
        String text = new NavBarItem(this, eachElement, myNodePopup).getText();
        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex != -1 && eachElement.equals(list.getSelectedValue())) {
          popupText.append("[" + text + "]");
        } else {
          popupText.append(text);
        }
        if (i < list.getModel().getSize() - 1) {
          popupText.append(">");
        }
      }
      info.put("navBarPopup", popupText.toString());
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public NavBarUI getNavBarUI() {
    return NavBarUIManager.getUI();
  }
}

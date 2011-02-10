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
package com.intellij.ide.navigationToolbar;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 */
public class NavBarPanel extends OpaquePanel.List implements DataProvider, PopupOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.navigationToolbar.NavigationToolbarPanel");
  private final NavBarModel myModel;

  private final NavBarPresentation myPresentation;
  private final Project myProject;

  private final ArrayList<NavBarItem> myList = new ArrayList<NavBarItem>();

  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private final IdeView myIdeView;
  private final CopyPasteDelegator myCopyPasteDelegator;
  private LightweightHint myHint = null;

  private ListPopupImpl myNodePopup = null;
  private JComponent myHintContainer;
  private Component myContextComponent;


  private Runnable myRunWhenListRebuilt;
  private final MergingUpdateQueue myUpdateQueue;
  private AtomicBoolean myModelUpdating = new AtomicBoolean(Boolean.FALSE);

  private NavBarItem myContextObject;
  private Alarm myUserActivityAlarm = new Alarm();
  private Runnable myUserActivityAlarmRunnable = new Runnable() {
    @Override
    public void run() {
      processUserActivity();
    }
  };
  private Runnable myUserActivityRunnable = new Runnable() {
    @Override
    public void run() {
      restartRebuild();
    }
  };

  public NavBarPanel(final Project project) {
    super(new FlowLayout(FlowLayout.LEFT, 5, 0), UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
    myProject = project;
    myModel = new NavBarModel(myProject);
    myIdeView = new NavBarIdeView(this);
    myPresentation = new NavBarPresentation(myProject);

    IdeEventQueue.getInstance().addActivityListener(myUserActivityRunnable);

    myUpdateQueue = new MergingUpdateQueue("NavBar", Registry.intValue("navbar.updateMergeTime"), true, MergingUpdateQueue.ANY_COMPONENT, project, null);

    PopupHandler.installPopupHandler(this, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.NAVIGATION_BAR);

    setBorder(new NavBarBorder(false, -1));

    myCopyPasteDelegator = new CopyPasteDelegator(myProject, NavBarPanel.this) {
      @NotNull
      protected PsiElement[] getSelectedElements() {
        final PsiElement element = getSelectedElement(PsiElement.class);
        return element == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{element};
      }
    };


    queueModelUpdateFromFocus();
    queueRebuildUi();
  }

  public ListPopupImpl getNodePopup() {
    return myNodePopup;
  }

  public NavBarPresentation getPresentation() {
    return myPresentation;
  }

  public void setContextComponent(Component contextComponent) {
    myContextComponent = contextComponent;
  }

  public ArrayList<NavBarItem> getItems() {
    return myList;
  }

  public void escape() {
    myModel.setSelectedIndex(-1);
    hideHint();
    ToolWindowManager.getInstance(myProject).activateEditorComponent();
  }

  public void enter() {
    final Object o = myModel.getSelectedValue();
    navigateInsideBar(optimizeTarget(o));
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
    if (myModel.getSelectedIndex() != -1) {
      ctrlClick(myModel.getSelectedIndex());
    }
  }

  public void moveEnd() {
    shiftFocus(myModel.size() - 1 - myModel.getSelectedIndex());
  }

  void restartRebuild() {
    myUserActivityAlarm.cancelAllRequests();
    myUserActivityAlarm.addRequest(myUserActivityAlarmRunnable, Registry.intValue("navbar.userActivityMergeTime"));
  }

  private void processUserActivity() {
    if (!isShowing()) {
      return;
    }

    IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        Window wnd = SwingUtilities.windowForComponent(NavBarPanel.this);
        if (wnd == null) return;

        Component focus = null;

        if (!wnd.isActive()) {
          IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, NavBarPanel.this);
          if (frame != null) {
            focus = IdeFocusManager.getInstance(myProject).getLastFocusedFor(frame);
          }
        } else {
          final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
          if (window instanceof Dialog) {
            final Dialog dialog = (Dialog)window;
            if (dialog.isModal() && !SwingUtilities.isDescendingFrom(NavBarPanel.this, dialog)) {
              return;
            }
          }
        }

        if (focus != null && focus.isShowing()) {
          queueModelUpdate(DataManager.getInstance().getDataContext(focus));
        } else if (wnd.isActive()) {
          queueModelUpdateFromFocus();
        }
      }
    });
  }

  private void queueModelUpdate(DataContext context) {
    _queueModelUpdate(context, null, false);
  }

  void queueModelUpdateFromFocus() {
    queueModelUpdateFromFocus(false);
  }

  private void queueModelUpdateFromFocus(boolean requeue) {
    _queueModelUpdate(null, myContextObject, requeue);
  }

  private void queueModelUpdateForObject(Object object) {
    _queueModelUpdate(null, object, false);
  }

  private void _queueModelUpdate(@Nullable final DataContext context, final @Nullable Object object, boolean requeue) {
    if (myModelUpdating.getAndSet(true) && !requeue) return;

    myUpdateQueue.cancelAllUpdates();

    myUpdateQueue.queue(new Update("model", 0) {
      @Override
      public void run() {
        if (context != null || object != null) {
          _updateModelFrom(context, object);
        }
        else {
          DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
            @Override
            public void run(DataContext dataContext) {
              _updateModelFrom(dataContext, null);
            }
          });
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        myModelUpdating.set(false);
      }
    });
  }

  private void _updateModelFrom(DataContext dataContext, Object object) {
    if (dataContext != null) {
      if (PlatformDataKeys.PROJECT.getData(dataContext) != myProject || isNodePopupShowing()) {
        queueModelUpdateFromFocus(true);
        return;
      }
      myModel.updateModel(dataContext);
    } else {
      myModel.updateModel(object);
    }

    queueRebuildUi();

    myModelUpdating.set(false);
  }

  void queueRebuildUi() {
    myUpdateQueue.queue(new AfterModel("ui", 1) {
      @Override
      protected void _run() {
        rebuildUi();
      }
    });
    queueRevalidate(null);
  }

  private void queueRevalidate(@Nullable final Runnable after) {
    myUpdateQueue.queue(new AfterModel("revalidate", 2) {
      @Override
      protected void _run() {
        if (myHint != null) {
          getHintContainerShowPoint().doWhenDone(new AsyncResult.Handler<RelativePoint>() {
            @Override
            public void run(final RelativePoint relativePoint) {
              myHint.setSize(getPreferredSize());
              myHint.setLocation(relativePoint);
              if (after != null) {
                after.run();
              }
            }
          });
        }
        else {
          if (after != null) {
            after.run();
          }
        }
      }
    });
  }

  private void queueSelect(final Runnable runnable) {
    myUpdateQueue.queue(new AfterModel("select", 3) {
      @Override
      protected void _run() {
        runnable.run();
      }
    });
  }

  private void queueAfterAll(final Runnable runnable, Object identity) {
    myUpdateQueue.queue(new AfterModel(identity, 4) {
      @Override
      protected void _run() {
        runnable.run();
      }
    });
  }

  public Project getProject() {
    return myProject;
  }

  public NavBarModel getModel() {
    return myModel;
  }

  private abstract class AfterModel extends Update {
    private AfterModel(Object identity, int priority) {
      super(identity, priority);
    }

    @Override
    public void run() {
      if (myModelUpdating.get()) {
        myUpdateQueue.queue(this);
      }
      else {
        _run();
      }
    }

    protected abstract void _run();
  }

  private static Object optimizeTarget(Object target) {
    if (target instanceof PsiDirectory && ((PsiDirectory)target).getFiles().length == 0) {
      final PsiDirectory[] subDir = ((PsiDirectory)target).getSubdirectories();
      if (subDir.length == 1) {
        return optimizeTarget(subDir[0]);
      }
    }
    return target;
  }

  void updateItems() {
    for (NavBarItem item : myList) {
      item.update();
    }
  }

  public void selectTail() {
    queueModelUpdateFromFocus();
    queueRebuildUi();

    queueSelect(new Runnable() {
      @Override
      public void run() {
        if (!myList.isEmpty()) {
          myModel.setSelectedIndex(myList.size() - 1);
          IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
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
  private void shiftFocus(int direction) {
    myModel.setSelectedIndex(myModel.getIndexByModel(myModel.getSelectedIndex() + direction));
  }

  void scrollSelectionToVisible() {
    final int selectedIndex = myModel.getSelectedIndex();
    if (selectedIndex == -1 || selectedIndex >= myList.size()) return;

    NavBarItem selectedItem = myList.get(selectedIndex);
    Rectangle rect = selectedItem.getBounds();
    scrollRectToVisible(rect);
  }

  @Nullable
  private NavBarItem getItem(int index) {
    if (index != -1 && index < myList.size()) {
      return myList.get(index);
    }
    return null;
  }

  boolean isInFloatingMode() {
    return myHint != null && myHint.isVisible();
  }


  @Override
  public Dimension getPreferredSize() {
    if (!myList.isEmpty()) {
      return super.getPreferredSize();
    }
    else {
      return new NavBarItem(this, null, 0).getPreferredSize();
    }
  }

  private boolean isRebuildUiNeeded() {
    if (myList.size() == myModel.size()) {
      int index = 0;
      for (NavBarItem eachLabel : myList) {
        Object eachElement = myModel.get(index);
        if (eachLabel.getObject() == null || !eachLabel.getObject().equals(eachElement)) {
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

  private void rebuildUi() {
    if (!isRebuildUiNeeded()) return;

    myList.clear();
    for (int index = 0; index < myModel.size(); index++) {
      final Object object = myModel.get(index);
      final NavBarItem label = new NavBarItem(this, object, index);

      installActions(index, label);
      myList.add(label);

    }

    rebuildComponent();

    if (myRunWhenListRebuilt != null) {
      Runnable r = myRunWhenListRebuilt;
      myRunWhenListRebuilt = null;
      r.run();
    }
  }

  private void rebuildComponent() {
    removeAll();

    for (NavBarItem item : myList) {
      add(item);
    }

    revalidate();
    repaint();

    queueAfterAll(new Runnable() {
      @Override
      public void run() {
        scrollSelectionToVisible();
      }
    }, "scrollToVisible");
  }

  @Nullable
  Window getWindow() {
    return !isShowing() ? null : (Window)UIUtil.findUltimateParent(this);
  }

  // ------ NavBar actions -------------------------
  private void installActions(final int index, final NavBarItem component) {
    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2) {
          myModel.setSelectedIndex(index);
          IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
          doubleClick(index);
          e.consume();
        }
      }
    });

    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        if (SystemInfo.isWindows) {
          click(e);
        }
      }

      public void mousePressed(final MouseEvent e) {
        if (!SystemInfo.isWindows) {
          click(e);
        }
      }

      private void click(final MouseEvent e) {
        if (!e.isConsumed() && e.isPopupTrigger()) {
          myModel.setSelectedIndex(index);
          IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
          rightClick(index);
          e.consume();
        }
      }
    });

    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        if (SystemInfo.isWindows) {
          click(e);
        }
      }

      public void mousePressed(final MouseEvent e) {
        if (!SystemInfo.isWindows) {
          click(e);
        }
      }

      private void click(final MouseEvent e) {
        if (!e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 1) {
          ctrlClick(index);
          myModel.setSelectedIndex(index);
          e.consume();
        }
      }
    });
  }

  private void doubleClick(final int index) {
    doubleClick(myModel.getElement(index));
  }

  private void doubleClick(final Object object) {
    if (object instanceof Navigatable) {
      final Navigatable navigatable = (Navigatable)object;
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
      }
    }
    else if (object instanceof Module) {
      final ProjectView projectView = ProjectView.getInstance(myProject);
      final AbstractProjectViewPane projectViewPane = projectView.getProjectViewPaneById(projectView.getCurrentViewId());
      projectViewPane.selectModule((Module)object, true);
    }
    else if (object instanceof Project) {
      return;
    }
    hideHint();
  }

  private void ctrlClick(final int index) {
    if (isNodePopupShowing()) {
      cancelPopup();
      if (myModel.getSelectedIndex() == index) {
        return;
      }
    }

    final Object object = myModel.getElement(index);
    final java.util.List<Object> objects = myModel.getChildren(object);

    if (!objects.isEmpty()) {
      final Object[] siblings = new Object[objects.size()];
      final Icon[] icons = new Icon[objects.size()];
      for (int i = 0; i < objects.size(); i++) {
        siblings[i] = objects.get(i);
        icons[i] = NavBarPresentation.getIcon(siblings[i], false);
      }
      final NavBarItem item = getItem(index);
      LOG.assertTrue(item != null);
      final BaseListPopupStep<Object> step = new BaseListPopupStep<Object>("", siblings, icons) {
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @NotNull
        public String getTextFor(final Object value) {
          return NavBarPresentation.getPresentableText(value, null);
        }

        public boolean isSelectable(Object value) {
          return true;
        }

        public PopupStep onChosen(final Object selectedValue, final boolean finalChoice) {
          return doFinalStep(new Runnable() {
            public void run() {
              navigateInsideBar(optimizeTarget(selectedValue));
            }
          });
        }
      };
      step.setDefaultOptionIndex(index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0);
      myNodePopup = new ListPopupImpl(step) {
        protected ListCellRenderer getListElementRenderer() {
          return new NavBarListCellRenderer(myProject, NavBarPanel.this);
        }

        @Override
        public void cancel(InputEvent e) {
          super.cancel(e);
        }
      };
      myNodePopup.registerAction("left", KeyEvent.VK_LEFT, 0, new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          myNodePopup.goBack();
          shiftFocus(-1);
          restorePopup();
        }
      });
      myNodePopup.registerAction("right", KeyEvent.VK_RIGHT, 0, new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          myNodePopup.goBack();
          shiftFocus(1);
          restorePopup();
        }
      });

      ListenerUtil.addMouseListener(myNodePopup.getComponent(), new MouseAdapter() {
        public void mouseReleased(final MouseEvent e) {
          if (SystemInfo.isWindows) {
            click(e);
          }
        }

        public void mousePressed(final MouseEvent e) {
          if (!SystemInfo.isWindows) {
            click(e);
          }
        }

        private void click(final MouseEvent e) {
          if (!e.isConsumed() && e.isPopupTrigger()) {
            myModel.setSelectedIndex(index);
            IdeFocusManager.getInstance(myProject).requestFocus(NavBarPanel.this, true);
            rightClick(index);
            e.consume();
          }
        }
      });

      if (!isValid()) {
        validate();
      }

      if (item.isShowing() && step.getValues().size() > 0) {
        myNodePopup.showUnderneathOf(item);
      }
    }
  }

  private boolean isNodePopupShowing() {
    return myNodePopup != null && myNodePopup.isVisible();
  }

  private void navigateInsideBar(final Object object) {
    myContextObject = null;

    myUpdateQueue.cancelAllUpdates();

    queueModelUpdateForObject(object);
    queueRebuildUi();

    queueAfterAll(new Runnable() {
      public void run() {
        int index = myModel.indexOf(object);
        if (index >= 0) {
          myModel.setSelectedIndex(index);
        }

        if (myModel.hasChildren(object)) {
          restorePopup();
        }
        else {
          doubleClick(object);
        }
      }
    }, "navigateInside");
  }

  private void rightClick(final int index) {
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_POPUP);
    final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.NAVIGATION_BAR, group);
    final NavBarItem item = getItem(index);
    if (item != null) {
      popupMenu.getComponent().show(this, item.getX(), item.getY() + item.getHeight());
    }
  }

  private void restorePopup() {
    cancelPopup();
    ctrlClick(myModel.getSelectedIndex());
  }

  private void cancelPopup() {
    if (myNodePopup != null) {
      myNodePopup.cancel();
      myNodePopup = null;
    }
  }

  void hideHint() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
    }
  }

  @Nullable
  public Object getData(String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return !myProject.isDisposed() ? myProject : null;
    }
    if (LangDataKeys.MODULE.is(dataId)) {
      final Module module = getSelectedElement(Module.class);
      if (module != null && !module.isDisposed()) return module;
      final PsiElement element = getSelectedElement(PsiElement.class);
      if (element != null) {
        return ModuleUtil.findModuleForPsiElement(element);
      }
      return null;
    }
    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      final PsiDirectory directory = getSelectedElement(PsiDirectory.class);
      if (directory != null) {
        final VirtualFile dir = directory.getVirtualFile();
        if (ProjectRootsUtil.isModuleContentRoot(dir, myProject)) {
          return ModuleUtil.findModuleForPsiElement(directory);
        }
      }
      return null;
    }
    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      final PsiElement element = getSelectedElement(PsiElement.class);
      return element != null && element.isValid() ? element : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final PsiElement element = getSelectedElement(PsiElement.class);
      return element != null && element.isValid() ? new PsiElement[]{element} : null;
    }

    if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      PsiElement[] psiElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
      if (psiElements == null) return null;
      Set<VirtualFile> files = new LinkedHashSet<VirtualFile>();
      for (PsiElement element : psiElements) {
        if (element instanceof PsiFileSystemItem) {
          files.add(((PsiFileSystemItem)element).getVirtualFile());
        }
      }
      return files.size() > 0 ? VfsUtil.toVirtualFileArray(files) : null;
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
    Object selectedValue1 = myModel.getSelectedValue();
    if (selectedValue1 == null) {
      final int modelSize = myModel.size();
      if (modelSize > 0) {
        selectedValue1 = myModel.getElement(modelSize - 1);
      }
    }
    final Object selectedValue = selectedValue1;
    return selectedValue != null && klass.isAssignableFrom(selectedValue.getClass()) ? (T)selectedValue : null;
  }

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

  public void addNotify() {
    super.addNotify();
    NavBarListener.subscribeTo(this);
  }

  public void removeNotify() {
    super.removeNotify();
    NavBarListener.unsubscribeFrom(this);
  }

  public void updateState(final boolean show) {
    queueModelUpdateFromFocus();
    if (isShowing()) {
      queueRebuildUi();
      queueAfterAll(new Runnable() {
        @Override
        public void run() {
          final int selectedIndex = myModel.getSelectedIndex();
          if (show && selectedIndex > -1 && selectedIndex < myModel.size()) {
            final NavBarItem item = getItem(selectedIndex);
            if (item != null) {
              IdeFocusManager.getInstance(myProject).requestFocus(item, true);
            }
          }
        }
      }, "requestFocus");
    }
  }

  // ------ popup NavBar ----------
  public void showHint(@Nullable final Editor editor, final DataContext dataContext) {
    queueModelUpdate(dataContext);
    queueAfterAll(new Runnable() {
      @Override
      public void run() {
        if (myModel.isEmpty()) return;
        myHint = new LightweightHint(NavBarPanel.this) {
          public void hide() {
            super.hide();
            cancelPopup();
          }
        };
        myHint.setForceShowAsPopup(true);
        myHint.setFocusRequestor(NavBarPanel.this);
        registerKeyboardAction(new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            hideHint();
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW);
        final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (editor == null) {
          myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
          getHintContainerShowPoint().doWhenDone(new AsyncResult.Handler<RelativePoint>() {
            @Override
            public void run(RelativePoint relativePoint) {
              final Component owner = focusManager.getFocusOwner();
              final Component cmp = relativePoint.getComponent();
              if (cmp instanceof JComponent && cmp.isShowing()) {
                myHint.show((JComponent)cmp, relativePoint.getPoint().x, relativePoint.getPoint().y,
                            owner instanceof JComponent ? (JComponent)owner : null,
                            new HintHint(relativePoint.getComponent(), relativePoint.getPoint()));
              }
            }
          });
        }
        else {
          myHintContainer = editor.getContentComponent();
          getHintContainerShowPoint().doWhenDone(new AsyncResult.Handler<RelativePoint>() {
            @Override
            public void run(RelativePoint rp) {
              Point p = rp.getPointOn(myHintContainer).getPoint();
              HintManagerImpl.getInstanceImpl()
                .showEditorHint(myHint, editor, p, HintManagerImpl.HIDE_BY_ESCAPE, 0, true, new HintHint(editor, p));
            }
          });
        }
        selectTail();
      }
    }, "showHint");
  }

  private AsyncResult<RelativePoint> getHintContainerShowPoint() {
    final AsyncResult<RelativePoint> result = new AsyncResult<RelativePoint>();
    if (myHintContainer != null) {
      final Point p = AbstractPopup.getCenterOf(myHintContainer, this);
      p.y -= myHintContainer.getVisibleRect().height / 4;

      result.setDone(RelativePoint.fromScreen(p));
    }
    else {
      if (myContextComponent != null) {
        result.setDone(JBPopupFactory.getInstance().guessBestPopupLocation(DataManager.getInstance().getDataContext(myContextComponent)));
      }
      else {
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
          @Override
          public void run(DataContext dataContext) {
            myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
            result
              .setDone(JBPopupFactory.getInstance().guessBestPopupLocation(DataManager.getInstance().getDataContext(myContextComponent)));
          }
        });
      }
    }
    return result;
  }
}

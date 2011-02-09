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

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.Icons;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class NavBarPanel extends OpaquePanel.List implements DataProvider, PopupOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.navigationToolbar.NavigationToolbarPanel");
  private final ArrayList<MyItemLabel> myList = new ArrayList<MyItemLabel>();

  private final NavBarModel myModel;
  private final NavBarPresentation myPresentation;

  private final Project myProject;
  private Runnable myDetacher;

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

  private MyItemLabel myContextObject;

  private PropertyChangeListener myFocusListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if ("focusOwner".equals(evt.getPropertyName()) || "permanentFocusOwner".equals(evt.getPropertyName())) {
        restartRebuild();
      }
    }
  };

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

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-myModel.getSelectedIndex());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(myModel.size() - 1 - myModel.getSelectedIndex());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myModel.getSelectedIndex() != -1) {
          ctrlClick(myModel.getSelectedIndex());
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), WHEN_FOCUSED);

    final ActionListener dblClickAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myModel.getSelectedIndex() != -1) {
          doubleClick(myModel.getSelectedIndex());
        }
      }
    };

    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), WHEN_FOCUSED);

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        final Object o = myModel.getSelectedValue();
        navigateInsideBar(optimizeTarget(o));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        myModel.setSelectedIndex(-1);
        ToolWindowManager.getInstance(project).activateEditorComponent();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_FOCUSED);

    addFocusListener(new FocusListener() {
      public void focusGained(final FocusEvent e) {
        updateItems();

        if (!isInFloatingMode() && myList.size() > 0) {
          myContextObject = myList.get(myList.size() - 1);
        } else {
          myContextObject = null;
        }
      }

      public void focusLost(final FocusEvent e) {
        if (myProject.isDisposed()) {
          myContextObject = null;
          hideHint();
          return;
        }

        // required invokeLater since in current call sequence KeyboardFocusManager is not initialized yet
        // but future focused component
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            processFocusLost(e);
          }
        });
      }
    });

    installBorder(-1, false);

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

  private void restartRebuild() {
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

  private void queueModelUpdateFromFocus() {
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

  private void queueRebuildUi() {
    myUpdateQueue.queue(new AfterModel("ui", 1) {
      @Override
      protected void _run() {
        rebuildUi();
      }
    });
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

  private void processFocusLost(final FocusEvent e) {
    final boolean nodePopupInactive = myNodePopup == null || !myNodePopup.isVisible() || !myNodePopup.isFocused();
    boolean childPopupInactive = !JBPopupFactory.getInstance().isChildPopupFocused(this);
    if (nodePopupInactive && childPopupInactive) {
      final Component opposite = e.getOppositeComponent();
      if (opposite != null && opposite != this && !isAncestorOf(opposite) && !e.isTemporary()) {
        myContextObject = null;
        hideHint();
      }
    }

    updateItems();
  }

  private void updateItems() {
    for (MyItemLabel item : myList) {
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

  private void shiftFocus(int direction) {
    myModel.setSelectedIndex(myModel.getIndexByModel(myModel.getSelectedIndex() + direction));
  }

  private void scrollSelectionToVisible() {
    final int selectedIndex = myModel.getSelectedIndex();
    if (selectedIndex == -1 || selectedIndex >= myList.size()) return;

    MyItemLabel selectedItem = myList.get(selectedIndex);
    Rectangle rect = selectedItem.getBounds();
    scrollRectToVisible(rect);
  }

  @Nullable
  private MyItemLabel getItem(int index) {
    if (index != -1 && index < myList.size()) {
      return myList.get(index);
    }
    return null;
  }

  private boolean isInFloatingMode() {
    return myHint != null && myHint.isVisible();
  }


  @Override
  public Dimension getPreferredSize() {
    if (!myList.isEmpty()) {
      return super.getPreferredSize();
    }
    else {
      return new MyItemLabel(null, 0, Icons.DIRECTORY_OPEN_ICON, "Sample", SimpleTextAttributes.REGULAR_ATTRIBUTES).getPreferredSize();
    }
  }

  private boolean isRebuildUiNeeded() {
    if (myList.size() == myModel.size()) {
      int index = 0;
      for (MyItemLabel eachLabel : myList) {
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
      Icon closedIcon = NavBarPresentation.getIcon(object, false);
      Icon openIcon = NavBarPresentation.getIcon(object, true);

      if (closedIcon == null && openIcon != null) closedIcon = openIcon;
      if (openIcon == null && closedIcon != null) openIcon = closedIcon;
      if (openIcon == null) {
        openIcon = closedIcon = EmptyIcon.create(5);
      }

      final MyItemLabel label = new MyItemLabel(object,
                                                index,
                                                wrapIcon(openIcon, closedIcon, index),
                                                NavBarPresentation.getPresentableText(object, getWindow()),
                                                myPresentation.getTextAttributes(object, false));

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

  private Icon wrapIcon(final Icon openIcon, final Icon closedIcon, final int idx) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (myModel.getSelectedIndex() == idx && myNodePopup != null && myNodePopup.isVisible()) {
          openIcon.paintIcon(c, g, x, y);
        }
        else {
          closedIcon.paintIcon(c, g, x, y);
        }
      }

      public int getIconWidth() {
        return openIcon.getIconWidth();
      }

      public int getIconHeight() {
        return openIcon.getIconHeight();
      }
    };
  }

  private void rebuildComponent() {
    removeAll();

    for (MyItemLabel item : myList) {
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

  Window getWindow() {
    return !isShowing() ? null : (Window)UIUtil.findUltimateParent(this);
  }

  // ------ NavBar actions -------------------------
  private void installActions(final int index, final MyItemLabel component) {
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
      final MyItemLabel item = getItem(index);
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

        /*
        public void canceled() {
          super.canceled();
          item.getLabel().setIcon(wrapIcon(NavBarModel.getIcon(object), index, Color.gray));
        }
        */
      };
      step.setDefaultOptionIndex(index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0);
      myNodePopup = new ListPopupImpl(step) {
        protected ListCellRenderer getListElementRenderer() {
          return new NavBarListCellRenderer(myProject, NavBarPanel.this);
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
    final MyItemLabel item = getItem(index);
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
      final MyItemLabel item = getItem(index);
      if (item != null) {
        return new Point(item.getX(), item.getY() + item.getHeight());
      }
    }
    return null;
  }

  // ----- inplace NavBar -----------
  public void installListeners() {
    final MyPsiTreeChangeAdapter psiListener = new MyPsiTreeChangeAdapter();
    final MyProblemListener problemListener = new MyProblemListener();
    final MyFileStatusListener fileStatusListener = new MyFileStatusListener();


    PsiManager.getInstance(myProject).addPsiTreeChangeListener(psiListener);
    WolfTheProblemSolver.getInstance(myProject).addProblemListener(problemListener);
    FileStatusManager.getInstance(myProject).addFileStatusListener(fileStatusListener);


    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myFocusListener);

    final MessageBusConnection busConnection = myProject.getMessageBus().connect();
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
    busConnection.subscribe(NavBarModelListener.NAV_BAR, new NavBarModelListener() {
      public void modelChanged() {
        queueRebuildUi();
      }

      public void selectionChanged() {
        updateItems();

        scrollSelectionToVisible();
      }
    });

    if (myDetacher != null) uninstallListeners();

    myDetacher = new Runnable() {
      public void run() {
        busConnection.disconnect();

        WolfTheProblemSolver.getInstance(myProject).removeProblemListener(problemListener);
        PsiManager.getInstance(myProject).removePsiTreeChangeListener(psiListener);
        FileStatusManager.getInstance(myProject).removeFileStatusListener(fileStatusListener);
      }
    };
  }

  public void uninstallListeners() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myFocusListener);
    IdeEventQueue.getInstance().removeActivityListener(myUserActivityRunnable);
    myDetacher.run();
    myDetacher = null;
  }

  public void installBorder(final int rightOffset, final boolean isDocked) {
    setBorder(new Border() {
      public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
        if (!isDocked) return;

        g.setColor(c.getBackground() != null ? c.getBackground().darker() : Color.darkGray);

        boolean drawTopBorder = true;
        if (isDocked) {
          if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
            drawTopBorder = false;
          }
        }

        if (rightOffset == -1) {
          if (drawTopBorder) {
            g.drawLine(0, 0, width - 1, 0);
          }
        }
        else {
          if (drawTopBorder) {
            g.drawLine(0, 0, width - rightOffset + 3, 0);
          }
        }
        g.drawLine(0, height - 1, width, height - 1);

        if (rightOffset == -1) {
          g.drawLine(0, 0, 0, height);
          g.drawLine(width - 1, 0, width - 1, height - 1);
        }
      }

      public Insets getBorderInsets(final Component c) {
        return new Insets(3, 4, 3, 4);
      }

      public boolean isBorderOpaque() {
        return true;
      }
    });
  }

  public void addNotify() {
    super.addNotify();
    installListeners();
  }

  public void removeNotify() {
    super.removeNotify();
    uninstallListeners();
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
            final MyItemLabel item = getItem(selectedIndex);
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
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_FOCUSED);
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

  public static boolean wolfHasProblemFilesBeneath(final PsiElement scope) {
    return WolfTheProblemSolver.getInstance(scope.getProject()).hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        if (scope instanceof PsiDirectory) {
          final PsiDirectory directory = (PsiDirectory)scope;
          if (!VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) return false;
          return ModuleUtil.findModuleForFile(virtualFile, scope.getProject()) == ModuleUtil.findModuleForPsiElement(scope);
        }
        else if (scope instanceof PsiDirectoryContainer) { // TODO: remove. It doesn't look like we'll have packages in navbar ever again
          final PsiDirectory[] psiDirectories = ((PsiDirectoryContainer)scope).getDirectories();
          for (PsiDirectory directory : psiDirectories) {
            if (VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) {
              return true;
            }
          }
        }
        return false;
      }
    });
  }

  protected class MyItemLabel extends SimpleColoredComponent {
    private final String myText;
    private final SimpleTextAttributes myAttributes;
    private final int myIndex;
    private final Icon myIcon;
    private Object myObject;

    public MyItemLabel(Object object, int idx, Icon icon, String presentableText, SimpleTextAttributes textAttributes) {
      myObject = object;
      myIndex = idx;
      myText = presentableText;
      myIcon = icon;
      myAttributes = textAttributes;

      setIpad(new Insets(1, 2, 1, 2));

      update();
    }

    public Object getObject() {
      return myObject;
    }

    public SimpleTextAttributes getAttributes() {
      return myAttributes;
    }

    private void update() {
      clear();

      setIcon(myIcon);
      boolean focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == NavBarPanel.this;

      boolean selected = myModel.getSelectedIndex() == myIndex;

      setPaintFocusBorder(!focused && selected);
      setFocusBorderAroundIcon(false);

      setBackground(selected && focused
                    ? UIUtil.getListSelectionBackground()
                    : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground()));

      final Color fg = selected && focused
                       ? UIUtil.getListSelectionForeground()
                       : myModel.getSelectedIndex() < myIndex && myModel.getSelectedIndex() != -1
                         ? UIUtil.getInactiveTextColor()
                         : myAttributes.getFgColor();

      final Color bg = selected && focused ? UIUtil.getListSelectionBackground() : myAttributes.getBgColor();

      append(myText, new SimpleTextAttributes(bg, fg, myAttributes.getWaveColor(), myAttributes.getStyle()));

      repaint();
    }
  }

  private class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    public void childAdded(PsiTreeChangeEvent event) {
      queueModelUpdateFromFocus();
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      queueModelUpdateFromFocus();
    }

    public void childMoved(PsiTreeChangeEvent event) {
      queueModelUpdateFromFocus();
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      queueModelUpdateFromFocus();
    }

    public void propertyChanged(final PsiTreeChangeEvent event) {
      queueModelUpdateFromFocus();
    }
  }

  private class MyModuleRootListener implements ModuleRootListener {
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      queueModelUpdateFromFocus();
    }
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {

    public void problemsAppeared(VirtualFile file) {
      queueModelUpdateFromFocus();
    }

    public void problemsDisappeared(VirtualFile file) {
      queueModelUpdateFromFocus();
    }

  }

  private class MyFileStatusListener implements FileStatusListener {

    public void fileStatusesChanged() {
      queueRebuildUi();
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      queueRebuildUi();
    }
  }
}

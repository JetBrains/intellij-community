// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.actions.ShowDiscoveredTestsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.PowerStatus;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE;

public class AffectedTestsInChangeListPainter implements ChangeListDecorator, ProjectComponent {
  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final ChangeListAdapter myChangeListListener;
  private final Alarm myAlarm;
  private final Set<String> myCache = new HashSet<>();

  public AffectedTestsInChangeListPainter(@NotNull Project project, ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myChangeListListener = new ChangeListAdapter() {
      @Override
      public void changeListsChanged() {
        scheduleUpdate();
      }

      @Override
      public void changeListUpdateDone() {
        scheduleUpdate();
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList, boolean automatic) {
        scheduleUpdate();
      }

      @Override
      public void unchangedFileStatusChanged() {
        scheduleUpdate();
      }
    };
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myChangeListManager.addChangeListListener(myChangeListListener);
  }

  @Override
  public void projectOpened() {
    DumbService.getInstance(myProject).runWhenSmart(() -> scheduleUpdate());
  }

  @Override
  public void projectClosed() {
    myAlarm.cancelAllRequests();
  }

  @Override
  public void disposeComponent() {
    myAlarm.cancelAllRequests();
    myCache.clear();
    myChangeListManager.removeChangeListListener(myChangeListListener);
  }

  private static int updateDelay() {
    return PowerStatus.getPowerStatus() == PowerStatus.AC ? 50 : 300;
  }

  @Override
  public void decorateChangeList(LocalChangeList changeList,
                                 ColoredTreeCellRenderer renderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowDiscoveredTestsAction.isEnabled(myProject)) return;
    if (changeList.getChanges().isEmpty()) return;
    if (!myCache.contains(changeList.getId())) return;

    renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    renderer.append("show affected tests", new SimpleTextAttributes(STYLE_UNDERLINE, UIUtil.getInactiveTextColor()), (Runnable)() -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(renderer.getTree());
      Change[] changes = ArrayUtil.toObjectArray(changeList.getChanges(), Change.class);
      ShowDiscoveredTestsAction.showDiscoveredTestsByChanges(myProject, changes, changeList.getName(), dataContext);
    });
  }

  private void scheduleUpdate() {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowDiscoveredTestsAction.isEnabled(myProject)) return;
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> update(), updateDelay());
  }

  private void update() {
    myCache.clear();
    List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    for (LocalChangeList list : lists) {
      if (list.getChanges().isEmpty()) continue;

      PsiMethod[] methods = ShowDiscoveredTestsAction.findMethods(myProject, ArrayUtil.toObjectArray(list.getChanges(), Change.class));
      if (methods.length == 0) continue;
      ReadAction.run(
        () -> ShowDiscoveredTestsAction.processMethods(myProject, methods, (clazz, method, parameter) -> {
          myCache.add(list.getId());
          return false;
        }, () -> ChangesViewManager.getInstance(myProject).scheduleRefresh()));
    }
  }
}
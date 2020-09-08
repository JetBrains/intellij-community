// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testDiscovery.actions.ShowAffectedTestsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.io.PowerStatus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE;

public class AffectedTestsInChangeListPainter implements ChangeListDecorator {
  private final Project myProject;
  private final Alarm myAlarm;
  private final AtomicReference<Set<String>> myChangeListsToShow = new AtomicReference<>(Collections.emptySet());

  public AffectedTestsInChangeListPainter(@NotNull Project project) {
    myProject = project;
    ChangeListListener changeListListener = new ChangeListAdapter() {
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
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ChangeListListener.TOPIC, changeListListener);
    DumbService.getInstance(myProject).runWhenSmart(() -> scheduleUpdate());
  }

  private void scheduleRefresh() {
    if (!myProject.isDisposed()) {
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  private static int updateDelay() {
    return PowerStatus.getPowerStatus() == PowerStatus.AC ? 50 : 300;
  }

  @Override
  public void decorateChangeList(@NotNull LocalChangeList changeList,
                                 @NotNull ColoredTreeCellRenderer renderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowAffectedTestsAction.isEnabled(myProject)) return;
    if (changeList.getChanges().isEmpty()) return;
    if (!myChangeListsToShow.get().contains(changeList.getId())) return;

    renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    renderer.append(JavaCompilerBundle.message("test.discovery.show.affected.tests"), new SimpleTextAttributes(STYLE_UNDERLINE, UIUtil.getInactiveTextColor()), (Runnable)() -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(renderer.getTree());
      Change[] changes = changeList.getChanges().toArray(new Change[0]);
      ShowAffectedTestsAction.showDiscoveredTestsByChanges(myProject, changes, changeList.getName(), dataContext);
    });
  }

  private void scheduleUpdate() {
    if (!Registry.is("show.affected.tests.in.changelists")) return;
    if (!ShowAffectedTestsAction.isEnabled(myProject)) return;
    myAlarm.cancelAllRequests();
    if (!myAlarm.isDisposed()) {
      myAlarm.addRequest(() -> update(), updateDelay());
    }
  }

  private void update() {
    myChangeListsToShow.set(
      ChangeListManager.getInstance(myProject).getChangeLists().stream()
        .filter(list -> !list.getChanges().isEmpty())
        .map(list -> {
          Collection<Change> changes = list.getChanges();

          PsiMethod[] methods = ShowAffectedTestsAction.findMethods(myProject, changes.toArray(new Change[0]));
          List<String> paths = ShowAffectedTestsAction.getRelativeAffectedPaths(myProject, changes);
          if (methods.length == 0 && paths.isEmpty()) return null;

          Ref<String> ref = Ref.create();
          ShowAffectedTestsAction.processMethods(myProject, methods, paths, (clazz, method, parameter) -> {
            ref.set(list.getId());
            return false;
          });
          return ref.get();
        }).filter(Objects::nonNull).collect(Collectors.toSet())
    );

    EdtInvocationManager.getInstance().invokeLater(this::scheduleRefresh);
  }
}
/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Queue;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
public class DumbServiceImpl extends DumbService {
  private final AtomicBoolean myDumb = new AtomicBoolean();
  private final DumbModeListener myPublisher;
  private final Queue<Runnable> myUpdateQueue = new Queue<Runnable>(5);
  private final Queue<Runnable> myAfterUpdateQueue = new Queue<Runnable>(5);
  @NonNls public static final String FILE_INDEX_BACKGROUND = "fileIndex.background";

  public DumbServiceImpl(MessageBus bus) {
    myPublisher = bus.syncPublisher(DUMB_MODE);
  }

  public boolean isDumb() {
    return myDumb.get();
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  @Override
  public void runWhenSmart(Runnable runnable) {
    if (!isDumb()) {
      runnable.run();
    } else {
      synchronized (myAfterUpdateQueue) {
        myAfterUpdateQueue.addLast(runnable);
      }
    }
  }

  public void queueIndexUpdate(@Nullable final Project project, @NotNull final Consumer<ProgressIndicator> action) {
    final Runnable update = new Runnable() {
      public void run() {
        if (project != null && project.isDisposed()) {
          updateFinished(project);
          return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating indices", false) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            indicator.setIndeterminate(false);
            indicator.setText("Indexing...");
            try {
              action.consume(indicator);
            }
            finally {
              ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
                public void run() {
                  updateFinished(project);
                }
              });
            }
          }
        });
      }
    };

    invokeOnEDT(new Runnable() {
      public void run() {
        myPublisher.beforeEnteringDumbMode();

        final boolean wasDumb = ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
          public Boolean compute() {
            final boolean wasDumb = myDumb.getAndSet(true);
            if (!wasDumb) {
              myPublisher.enteredDumbMode();
            }
            return wasDumb;
          }
        }).booleanValue();

        if (wasDumb) {
          myUpdateQueue.addLast(update);
        } else {
          update.run();
        }
      }
    });
  }

  private static void invokeOnEDT(Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL);
    }
  }

  private void updateFinished(Project project) {
    if (!myUpdateQueue.isEmpty()) {
      //run next dumb action
      myUpdateQueue.pullFirst().run();
      return;
    }

    //leave dumb mode
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myDumb.set(false);
        myPublisher.exitDumbMode();
      }
    });

    if (project == null || project.isDisposed()) return;

    while (true) {
      final Runnable runnable;
      synchronized (myAfterUpdateQueue) {
        if (myAfterUpdateQueue.isEmpty()) {
          break;
        }

        runnable = myAfterUpdateQueue.pullFirst();
      }
      runnable.run();
    }
  }


}

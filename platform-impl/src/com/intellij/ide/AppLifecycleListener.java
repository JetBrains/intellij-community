package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface AppLifecycleListener {
  Topic<AppLifecycleListener> TOPIC = Topic.create("Application lifecycle notifications", AppLifecycleListener.class);

  void appFrameCreated(final String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject);
  void appStarting(Project projectFromCommandLine);
  void projectFrameClosed();
  void projectOpenFailed();

  abstract class Adapter implements AppLifecycleListener {
    public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
    }

    public void appStarting(final Project projectFromCommandLine) {
    }

    public void projectFrameClosed() {
    }

    public void projectOpenFailed() {
    }
  }
}
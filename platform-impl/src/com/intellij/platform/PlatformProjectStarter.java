/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PlatformProjectStarter implements ApplicationComponent {
  public PlatformProjectStarter(MessageBus bus) {
    bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
        if (commandLineArgs.length > 0) {
          willOpenProject.set(true);
        }
      }
    });
  }

  public void disposeComponent() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "platform.ProjectStarter";
  }

  public void initComponent() {
  }
}
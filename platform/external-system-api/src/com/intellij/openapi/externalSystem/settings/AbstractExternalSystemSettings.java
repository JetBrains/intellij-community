// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;

/**
 * Common base class for external system settings. Defines a minimal api which is necessary for the common external system
 * support codebase.
 * <p/>
 * <b>Note:</b> non-abstract subclasses of this class are expected to be marked by {@link State} annotation configured as necessary.
 *
 * @author Denis Zhdanov
 */
public abstract class AbstractExternalSystemSettings<
  SS extends AbstractExternalSystemSettings<SS, PS, L>,
  PS extends ExternalProjectSettings,
  L extends ExternalSystemSettingsListener<PS>>
  implements Disposable {

  @NotNull private final NullableLazyValue<ExternalSystemManager<?, ?, ?, ?, ?>> myManager;
  @NotNull private final Topic<L> myChangesTopic;
  @NotNull private final Project myProject;

  @NotNull private final Map<String/* project path */, PS> myLinkedProjectsSettings = new HashMap<>();

  @NotNull private final Map<String/* project path */, PS> myLinkedProjectsSettingsView
    = Collections.unmodifiableMap(myLinkedProjectsSettings);

  protected AbstractExternalSystemSettings(@NotNull Topic<L> topic, @NotNull Project project) {
    myChangesTopic = topic;
    myProject = project;
    myManager = atomicLazyNullable(this::deduceManager);
  }

  @Override
  public void dispose() {

  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private @Nullable ExternalSystemManager<?, ?, ?, ?, ?> deduceManager() {
    return ExternalSystemApiUtil.getAllManagers().stream()
      .filter(it -> equals(it.getSettingsProvider().fun(myProject)))
      .findFirst()
      .orElse(null);
  }

  public boolean showSelectiveImportDialogOnInitialImport() {
    return Boolean.getBoolean("external.system.show.selective.import.dialog");
  }

  /**
   * Every time particular external system setting is changed corresponding message is sent via ide
   * <a href="https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Messaging+infrastructure">messaging sub-system</a>.
   * The problem is that every external system implementation defines it's own topic/listener pair. Listener interface is derived
   * from the common {@link ExternalSystemSettingsListener} interface and is specific to external sub-system implementation.
   * However, it's possible that a client wants to perform particular actions based only on {@link ExternalSystemSettingsListener}
   * facilities. There is no way for such external system-agnostic client to create external system-specific listener
   * implementation then.
   * <p/>
   * That's why this method allows to wrap given 'generic listener' into external system-specific one.
   *
   * @param listener         target generic listener to wrap to external system-specific implementation
   * @param parentDisposable is a disposable to unsubscribe from external system settings events
   * @note lifetime of parentDisposable must be shorter of project lifetime
   * @abstract at 2021
   */
  public void subscribe(@NotNull ExternalSystemSettingsListener<PS> listener, @NotNull Disposable parentDisposable) {
    subscribe(listener); // Api backward compatibility
  }

  /**
   * @remove at 2021
   * @see AbstractExternalSystemSettings#subscribe(ExternalSystemSettingsListener, Disposable)
   * @deprecated use/implements {@link AbstractExternalSystemSettings#subscribe(ExternalSystemSettingsListener, Disposable)} instead
   */
  @Deprecated
  public abstract void subscribe(@NotNull ExternalSystemSettingsListener<PS> listener);

  /**
   * Generic subscribe implementation
   *
   * @see AbstractExternalSystemSettings#subscribe(ExternalSystemSettingsListener, Disposable)
   */
  protected void doSubscribe(@NotNull L listener, @NotNull Disposable parentDisposable) {
    MessageBus messageBus = myProject.getMessageBus();
    MessageBusConnection connection = messageBus.connect(parentDisposable);
    connection.subscribe(getChangesTopic(), listener);
  }

  public void copyFrom(@NotNull SS settings) {
    for (PS projectSettings : settings.getLinkedProjectsSettings()) {
      myLinkedProjectsSettings.put(projectSettings.getExternalProjectPath(), projectSettings);
    }
    copyExtraSettingsFrom(settings);
  }

  protected abstract void copyExtraSettingsFrom(@NotNull SS settings);

  @NotNull
  public Collection<PS> getLinkedProjectsSettings() {
    return myLinkedProjectsSettingsView.values();
  }

  @Nullable
  public PS getLinkedProjectSettings(@NotNull String linkedProjectPath) {
    PS ps = myLinkedProjectsSettings.get(linkedProjectPath);
    if (ps == null) {
      for (PS ps1 : myLinkedProjectsSettings.values()) {
        for (String modulePath : ps1.getModules()) {
          if (linkedProjectPath.equals(modulePath)) return ps1;
        }
      }
    }
    return ps;
  }

  public void linkProject(@NotNull PS settings) throws IllegalArgumentException {
    PS existing = getLinkedProjectSettings(settings.getExternalProjectPath());
    if (existing != null) {
      throw new IllegalArgumentException(String.format(
        "Can't link project '%s'. Reason: it's already linked to the IDE project",
        settings.getExternalProjectPath()
      ));
    }
    myLinkedProjectsSettings.put(settings.getExternalProjectPath(), settings);
    onProjectsLinked(Collections.singleton(settings));
  }

  /**
   * Un-links given external project from the current ide project.
   *
   * @param linkedProjectPath path of external project to be unlinked
   * @return {@code true} if there was an external project with the given config path linked to the current
   * ide project;
   * {@code false} otherwise
   */
  public boolean unlinkExternalProject(@NotNull String linkedProjectPath) {
    PS removed = myLinkedProjectsSettings.remove(linkedProjectPath);
    if (removed == null) {
      return false;
    }

    onProjectsUnlinked(Collections.singleton(linkedProjectPath));
    return true;
  }

  public void setLinkedProjectsSettings(@NotNull Collection<? extends PS> settings) {
    setLinkedProjectsSettings(settings, new ExternalSystemSettingsListenerAdapter<>() {
      @Override
      public void onProjectsLinked(@NotNull Collection<PS> settings) {
        AbstractExternalSystemSettings.this.onProjectsLinked(settings);
      }

      @Override
      public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
        AbstractExternalSystemSettings.this.onProjectsUnlinked(linkedProjectPaths);
      }
    });
  }

  private void setLinkedProjectsSettings(@NotNull Collection<? extends PS> settings, @NotNull ExternalSystemSettingsListener<PS> listener) {
    // do not add invalid 'null' settings
    settings = ContainerUtil.filter(settings, ps -> ps.getExternalProjectPath() != null);

    List<PS> added = new ArrayList<>();
    Map<String, PS> removed = new HashMap<>(myLinkedProjectsSettings);
    myLinkedProjectsSettings.clear();
    for (PS current : settings) {
      myLinkedProjectsSettings.put(current.getExternalProjectPath(), current);
    }

    for (PS current : settings) {
      PS old = removed.remove(current.getExternalProjectPath());
      if (old == null) {
        added.add(current);
      }
      else {
        checkSettings(old, current);
      }
    }
    if (!added.isEmpty()) {
      listener.onProjectsLinked(added);
    }
    if (!removed.isEmpty()) {
      listener.onProjectsUnlinked(removed.keySet());
    }
  }

  /**
   * Is assumed to check if given old settings external system-specific state differs from the given new one
   * and {@link #getPublisher() notify} listeners in case of the positive answer.
   *
   * @param old     old settings state
   * @param current current settings state
   */
  protected abstract void checkSettings(@NotNull PS old, @NotNull PS current);

  @NotNull
  public Topic<L> getChangesTopic() {
    return myChangesTopic;
  }

  @NotNull
  public L getPublisher() {
    return myProject.getMessageBus().syncPublisher(myChangesTopic);
  }

  protected void fillState(@NotNull State<PS> state) {
    state.setLinkedExternalProjectsSettings(new TreeSet<>(myLinkedProjectsSettings.values()));
  }

  protected void loadState(@NotNull State<PS> state) {
    Set<PS> settings = state.getLinkedExternalProjectsSettings();
    if (settings != null) {
      setLinkedProjectsSettings(settings, new ExternalSystemSettingsListenerAdapter<>() {
        @Override
        public void onProjectsLinked(@NotNull Collection<PS> settings) {
          ApplicationManager.getApplication().invokeLater(() -> {
            AbstractExternalSystemSettings.this.onProjectsLinked(settings);
            AbstractExternalSystemSettings.this.onProjectsLoaded(settings);
          }, myProject.getDisposed());
        }

        @Override
        public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
          ApplicationManager.getApplication().invokeLater(() -> {
            AbstractExternalSystemSettings.this.onProjectsUnlinked(linkedProjectPaths);
          }, myProject.getDisposed());
        }
      });
    }
  }

  private void onProjectsLoaded(@NotNull Collection<PS> settings) {
    getPublisher().onProjectsLoaded(settings);
    ExternalSystemManager<?, ?, ?, ?, ?> manager = myManager.getValue();
    if (manager != null) ExternalSystemSettingsListenerEx.Companion.onProjectsLoaded(myProject, manager, settings);
  }

  private void onProjectsLinked(@NotNull Collection<PS> settings) {
    getPublisher().onProjectsLinked(settings);
    ExternalSystemManager<?, ?, ?, ?, ?> manager = myManager.getValue();
    if (manager != null) ExternalSystemSettingsListenerEx.Companion.onProjectsLinked(myProject, manager, settings);
  }

  private void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
    getPublisher().onProjectsUnlinked(linkedProjectPaths);
    ExternalSystemManager<?, ?, ?, ?, ?> manager = myManager.getValue();
    if (manager != null) ExternalSystemSettingsListenerEx.Companion.onProjectsUnlinked(myProject, manager, linkedProjectPaths);
  }

  public interface State<S> {

    Set<S> getLinkedExternalProjectsSettings();

    void setLinkedExternalProjectsSettings(Set<S> settings);
  }
}

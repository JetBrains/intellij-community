package com.intellij.database.extensions;

import com.intellij.database.DataGridBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.ide.script.IdeScriptEngineManager;
import com.intellij.ide.script.IdeScriptException;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;

import static com.intellij.database.datagrid.DataGridNotifications.EXTRACTORS_GROUP;

public final class ExtensionScriptsUtil {
  private static final String JS_PLUGIN_ID = "org.jetbrains.intellij.scripting-javascript";
  private static final String JS_PLUGIN_NAME = "IntelliJ Scripting: JavaScript";

  private ExtensionScriptsUtil() {
  }

  public static @Nullable IdeScriptEngine getEngineFor(@Nullable Project project,
                                                       @Nullable PluginId pluginId,
                                                       @NotNull Path file,
                                                       @Nullable BiConsumer<String, Project> installPlugin) {
    return getEngineFor(project, pluginId, file, installPlugin, true);
  }

  public static @Nullable IdeScriptEngine getEngineFor(@Nullable Project project,
                                                       @Nullable PluginId pluginId,
                                                       @NotNull Path file,
                                                       @Nullable BiConsumer<String, Project> installPlugin,
                                                       boolean showBalloon) {
    return getEngineFor(project, getClassLoader(pluginId), file, installPlugin, showBalloon);
  }

  public static @Nullable ClassLoader getClassLoader(@Nullable PluginId pluginId) {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
    plugin = plugin != null ? plugin : PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID);
    IdeaPluginDescriptor descriptor = Objects.requireNonNull(plugin);

    return descriptor.getPluginClassLoader();
  }

  public static @Nullable IdeScriptEngine getEngineFor(@Nullable Project project,
                                                     ClassLoader baseLoader,
                                                     @NotNull Path file,
                                                     @Nullable BiConsumer<String, Project> installPlugin,
                                                     boolean showBalloon) {
    String scriptExtension = FileUtilRt.getExtension(file.getFileName().toString());
    if ("js".equals(scriptExtension)) {
      System.setProperty("polyglot.js.nashorn-compat", "true");
    }
    ClassLoader loader = makeCancellable(baseLoader);
    IdeScriptEngine engine = IdeScriptEngineManager.getInstance().getEngineByFileExtension(scriptExtension, loader);
    if (engine != null || !showBalloon) return engine;
    showEngineNotFoundBalloon(project, installPlugin, scriptExtension);
    return null;
  }

  private static ClassLoader makeCancellable(ClassLoader loader) {
    return new ClassLoader("Cancellable Engine Classloader", loader) {
      @Override
      protected Object getClassLoadingLock(String className) {
        ProgressManager.checkCanceled();
        return super.getClassLoadingLock(className);
      }
    };
  }

  public static void showEngineNotFoundBalloon(@Nullable Project project, @Nullable BiConsumer<String, Project> installPlugin, String scriptExtension) {
    String title = DataGridBundle.message("notification.title.no.script.engine.found.for.file.extension", scriptExtension);
    if ("js".equals(scriptExtension)) {
      String content = DataGridBundle.message("notification.please.install.js.script.engine", JS_PLUGIN_NAME);
      Notification notification = EXTRACTORS_GROUP.createNotification(title, content, NotificationType.INFORMATION);
      if (installPlugin != null) {
        notification.addAction(new NotificationAction(DataGridBundle.message("notification.install.plugin")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            installPlugin.accept(JS_PLUGIN_ID, project);
          }
        });
      }
      notification.notify(project);
    }
    else {
      String content =
        DataGridBundle.message("notification.content.please.make.sure.your.script.engine.jar.s.language.runtime.are.in.ide.classpath");
      showError(project, title, content);
    }
  }

  public static @NotNull Binder setBindings(@NotNull IdeScriptEngine engine) {
    return new Binder(engine);
  }

  @SuppressWarnings("UnusedParameters")
  public static void prepareScript(@NotNull Path script) {
    ThreadingAssertions.assertEventDispatchThread();

    // a script itself, or some files it uses can be open in editor(s)
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  public static @NotNull String loadScript(@Nullable Project project, @NotNull Path script) throws IOException {
    try {
      return Files.readString(script);
    }
    catch (IOException e) {
      showError(project, DataGridBundle.message("notification.title.failed.to.load.script", script.toString()),
                ExceptionUtil.getThrowableText(e));
      throw e;
    }
  }

  public static Object evalScript(@Nullable Project project,
                                  @NotNull IdeScriptEngine engine,
                                  @NotNull Path scriptFile) throws IdeScriptException {
    return evalScript(project, engine, scriptFile, true);
  }

  public static Object evalScript(@Nullable Project project,
                                  @NotNull IdeScriptEngine engine,
                                  @NotNull Path scriptFile,
                                  boolean showErrorMessage) throws IdeScriptException {
    try {
      String script = loadScript(project, scriptFile);
      return engine.eval(script);
    }
    catch (IOException e) {
      throw new IdeScriptException("Failed to load " + scriptFile, e);
    }
    catch (IdeScriptException e) {
      ProgressManager.checkCanceled();
      ProcessCanceledException pce = ExceptionUtil.findCause(e, ProcessCanceledException.class);
      if (pce != null) throw pce;
      if (showErrorMessage) {
        showScriptExecutionError(project, scriptFile, ExceptionUtil.getRootCause(e));
      }
      throw e;
    }
  }

  public static void showScriptExecutionError(@Nullable Project project, @NotNull Path scriptFile, @NotNull Throwable error) {
    //noinspection HardCodedStringLiteral
    EXTRACTORS_GROUP.createNotification("<a href=\"generator\">" + scriptFile.getFileName() + "</a>: " + ExceptionUtil.getThrowableText(error, "com.intellij."),
        NotificationType.ERROR)
      .setListener((notification, event) -> navigateToFile(project, scriptFile))
      .notify(project);
  }

  public static void showError(@Nullable Project project, @NlsContexts.NotificationTitle @NotNull String title, @NlsContexts.NotificationContent @NotNull String content) {
    EXTRACTORS_GROUP.createNotification(title, content, NotificationType.ERROR).notify(project);
  }

  public static boolean navigateToFile(@Nullable Project project, @NotNull Path file) {
    ProjectFileIndex index = project == null ? null : ProjectFileIndex.getInstance(project);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
    if (index != null && virtualFile != null && virtualFile.isValid() &&
        (ScratchUtil.isScratch(virtualFile) || index.isInContent(virtualFile) || index.isInLibrary(virtualFile))) {
      new OpenFileDescriptor(project, virtualFile).navigate(true);
    }
    else if (Files.exists(file)) {
      IdeUiService.getInstance().revealFile(file);
    }
    else {
      return false;
    }
    return true;
  }

  public static class Binder {
    private final IdeScriptEngine myEngine;

    Binder(@NotNull IdeScriptEngine engine) {
      myEngine = engine;
    }

    public @NotNull <T> Binder bind(@NotNull Binding<T> what, @Nullable T to) {
      myEngine.setBinding(what.name, to);
      return this;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(@NotNull IdeScriptEngine engine, @NotNull Binding<T> what) {
      return (T)engine.getBinding(what.name);
    }
  }
}

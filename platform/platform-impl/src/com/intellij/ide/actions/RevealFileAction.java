// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.SystemProperties;
import com.intellij.util.system.OS;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

/**
 * This helpful action opens a file or directory in a system file manager.
 *
 * @see ShowFilePathAction
 */
public class RevealFileAction extends DumbAwareAction implements LightEditCompatible, ActionRemoteBehaviorSpecification.Disabled {
  private static final Logger LOG = Logger.getInstance(RevealFileAction.class);

  public static final NotificationListener FILE_SELECTING_LISTENER = new NotificationListener.Adapter() {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      var url = e.getURL();
      if (url != null) {
        try {
          openFile(Path.of(url.toURI()));
        }
        catch (InvalidPathException | URISyntaxException ex) {
          LOG.warn("invalid URL: " + url, ex);
        }
      }
      notification.hideBalloon();
    }
  };

  public RevealFileAction() {
    getTemplatePresentation().setText(getActionName(true));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var editor = e.getData(CommonDataKeys.EDITOR);
    e.getPresentation().setEnabledAndVisible(
      isSupported() &&
      getFile(e) != null &&
      (!e.isFromContextMenu() ||
       editor == null ||
       !editor.getSelectionModel().hasSelection() ||
       EditorUtil.contextMenuInvokedOutsideOfSelection(e)));
    e.getPresentation().setText(getActionName(e.getPlace()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var file = getFile(e);
    if (file != null) {
      openFile(file.toNioPath());
    }
  }

  private static @Nullable VirtualFile getFile(AnActionEvent e) {
    return findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
  }

  /** Whether a system is able to open a directory in a file manager and highlight a file in it. */
  public static boolean isSupported() {
    return OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS || Holder.fileManagerApp != null;
  }

  /** Whether a system is able to open a directory in a file manager. */
  public static boolean isDirectoryOpenSupported() {
    return OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS || Holder.fileManagerApp != null;
  }

  public static @ActionText @NotNull String getActionName() {
    return getActionName(null);
  }

  public static @ActionText @NotNull String getActionName(@Nullable String place) {
    var shortName = ActionPlaces.REVEAL_IN_POPUP.equals(place);
    return shortName ? getFileManagerName() : getActionName(false);
  }

  private static @ActionText String getActionName(boolean skipDetection) {
    return OS.CURRENT == OS.macOS ? ActionsBundle.message("action.RevealIn.name.mac") : ActionsBundle.message("action.RevealIn.name.other", getFileManagerName(skipDetection));
  }

  @Override
  public void applyTextOverride(@NotNull String place, @NotNull Presentation presentation) {
    if (ActionPlaces.REVEAL_IN_POPUP.equals(place)) {
      presentation.setText(getActionName(place));
    }
    else {
      super.applyTextOverride(place, presentation);
    }
  }

  public static @NotNull @ActionText String getFileManagerName() {
    return getFileManagerName(false);
  }

  public static @NotNull @ActionText String getFileManagerName(boolean skipDetection) {
    return OS.CURRENT == OS.Windows ? IdeBundle.message("action.explorer.text") :
           OS.CURRENT == OS.macOS ? IdeBundle.message("action.finder.text") :
           skipDetection ? IdeBundle.message("action.file.manager.text") :
           requireNonNullElseGet(Holder.fileManagerName, () -> IdeBundle.message("action.file.manager.text"));
  }

  public static @Nullable VirtualFile findLocalFile(@Nullable VirtualFile file) {
    if (file == null || file.isInLocalFileSystem()) {
      return file;
    }

    var fs = file.getFileSystem();
    if (fs instanceof ArchiveFileSystem && file.getParent() == null) {
      return ((ArchiveFileSystem)fs).getLocalByEntry(file);
    }

    return null;
  }

  /** Please use #openFile(Path) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "IO_FILE_USAGE"})
  public static void openFile(@NotNull java.io.File file) {
    openFile(file.toPath());
  }

  /**
   * Opens a system file manager with the given file's parent directory loaded and the file highlighted in it
   * (note that some platforms do not support the file highlighting).
   */
  public static void openFile(@NotNull Path file) {
    var parent = canonicalize(file).getParent();
    if (parent != null) {
      doOpen(parent, file);
    }
    else {
      doOpen(file, null);
    }
  }

  /** Please use #openDirectory(Path) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "IO_FILE_USAGE"})
  public static void openDirectory(@NotNull java.io.File directory) {
    doOpen(directory.toPath(), null);
  }

  /**
   * Opens a system file manager with the given directory loaded in it.
   */
  public static void openDirectory(@NotNull Path directory) {
    doOpen(directory, null);
  }

  private static void doOpen(@NotNull Path _dir, @Nullable Path _toSelect) {
    var dir = canonicalize(_dir).normalize().toString();
    var toSelect = _toSelect != null ? canonicalize(_toSelect).normalize().toString() : null;
    String fmApp;

    if (OS.CURRENT == OS.Windows) {
      if (JnaLoader.isLoaded()) {
        openViaShellApi(dir, toSelect);
      }
      else {
        openViaExplorerCall(dir, toSelect);
      }
    }
    else if (OS.CURRENT == OS.macOS) {
      if (toSelect != null) {
        spawn("open", "-R", toSelect);
      }
      else {
        spawn("open", dir);
      }
    }
    else if ((fmApp = Holder.fileManagerApp) != null) {
      if (toSelect != null && fmApp.endsWith("dolphin")) {
        spawn(fmApp, "--select", toSelect);
      }
      else if (toSelect != null && fmApp.endsWith("dde-file-manager")) {
        spawn(fmApp, "--show-item", toSelect);
      }
      else {
        spawn(fmApp, toSelect != null ? toSelect : dir);
      }
    }
    else if (toSelect == null && PathEnvironmentVariableUtil.isOnPath("xdg-open")) {
      spawn("xdg-open", dir);
    }
    else {
      var message = IdeBundle.message("reveal.unsupported.message", requireNonNullElse(toSelect, dir));
      new Notification("System Messages", message, NotificationType.WARNING)
        .notify(null);
    }
  }

  private static void openViaShellApi(String dir, @Nullable String toSelect) {
    if (LOG.isDebugEnabled()) LOG.debug("shell open: dir=" + dir + " toSelect=" + toSelect);

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);

      if (toSelect == null) {
        var res = Shell32.INSTANCE.ShellExecute(null, "explore", dir, null, null, WinUser.SW_NORMAL);
        if (res.intValue() <= 32) {
          LOG.warn("ShellExecute(" + dir + "): " + res.intValue() + " GetLastError=" + Kernel32.INSTANCE.GetLastError());
          openViaExplorerCall(dir, null);
        }
      }
      else {
        var pIdl = Shell32Ex.INSTANCE.ILCreateFromPath(dir);
        var apIdl = new Pointer[]{Shell32Ex.INSTANCE.ILCreateFromPath(toSelect)};
        var cIdl = new WinDef.UINT(apIdl.length);
        try {
          var res = Shell32Ex.INSTANCE.SHOpenFolderAndSelectItems(pIdl, cIdl, apIdl, new WinDef.DWORD(0));
          if (!WinError.S_OK.equals(res)) {
            LOG.warn("SHOpenFolderAndSelectItems(" + dir + ',' + toSelect + "): 0x" + Integer.toHexString(res.intValue()));
            openViaExplorerCall(dir, toSelect);
          }
        }
        finally {
          Shell32Ex.INSTANCE.ILFree(pIdl);
          Shell32Ex.INSTANCE.ILFree(apIdl[0]);
        }
      }
    });
  }

  private static Path canonicalize(@NotNull Path path) {
    try {
      return path.toRealPath();
    }
    catch (IOException e) {
      LOG.info("Could not convert " + path + " to canonical path", e);
      return path.toAbsolutePath();
    }
  }

  private static void openViaExplorerCall(String dir, @Nullable String toSelect) {
    spawn(toSelect != null ? "explorer /select,\"" + toSelect + '"' : "explorer /root,\"" + dir + '"');
  }

  private interface Shell32Ex extends StdCallLibrary {
    Shell32Ex INSTANCE = Native.load("shell32", Shell32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

    Pointer ILCreateFromPath(String path);
    void ILFree(Pointer pIdl);
    WinNT.HRESULT SHOpenFolderAndSelectItems(Pointer pIdlFolder, WinDef.UINT cIdl, Pointer[] apIdl, WinDef.DWORD dwFlags);
  }

  private static void spawn(String... command) {
    if (LOG.isDebugEnabled()) LOG.debug(Arrays.toString(command));

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      try {
        var process = OS.CURRENT == OS.Windows ? Runtime.getRuntime().exec(command[0]) : new ProcessBuilder(command).start();
        new CapturingProcessHandler.Silent(process, null, command[0])
          .runProcess(10000, false)
          .checkSuccess(LOG);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }

  private static final class Holder {
    private static final @Nullable String fileManagerApp;
    private static final @Nullable @NlsSafe String fileManagerName;

    static {
      String fmApp = null, fmName = null;
      if (PathEnvironmentVariableUtil.isOnPath("xdg-mime")) {
        try (var reader = new ProcessBuilder("xdg-mime", "query", "default", "inode/directory").start().inputReader()) {
          var desktopEntryName = reader.readLine();
          if (desktopEntryName != null && desktopEntryName.endsWith(".desktop")) {
            var desktopFile = Stream.of(getXdgDataDirectories().split(":"))
              .map(dir -> Path.of(dir, "applications", desktopEntryName))
              .filter(Files::exists)
              .findFirst();
            if (desktopFile.isPresent()) {
              var lines = Files.readAllLines(desktopFile.get());
              fmApp = lines.stream()
                .filter(line -> line.startsWith("Exec="))
                .map(line -> getExecCommand(line.substring(5)))
                .findFirst().orElse(null);
              fmName = lines.stream()
                .filter(line -> line.startsWith("Name="))
                .map(line -> line.substring(5))
                .findFirst().orElse(null);
            }
          }
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
      fileManagerApp = fmApp;
      fileManagerName = fmName;
    }

    private static String getXdgDataDirectories() {
      return requireNonNullElse(System.getenv("XDG_DATA_HOME"), SystemProperties.getUserHome() + "/.local/share") + ':' +
             requireNonNullElse(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
    }

    private static String getExecCommand(String value) {
      if (value.startsWith("\"")) {
        int p = value.lastIndexOf('\"');
        if (p > 1) {
          return value.substring(1, p);
        }
      }
      return value.split(" ")[0];
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated trivial to implement, just inline */
  @Deprecated(forRemoval = true)
  public static void showDialog(
    Project project,
    @NlsContexts.DialogMessage String message,
    @NlsContexts.DialogTitle String title,
    @SuppressWarnings({"UnnecessaryFullyQualifiedName", "IO_FILE_USAGE"}) @NotNull java.io.File file,
    @Nullable DoNotAskOption option
  ) {
    if (MessageDialogBuilder.okCancel(title, message)
      .yesText(getActionName(null))
      .noText(IdeBundle.message("action.close"))
      .icon(Messages.getInformationIcon())
      .doNotAsk(option)
      .ask(project)) {
      openFile(file);
    }
  }
  //</editor-fold>
}

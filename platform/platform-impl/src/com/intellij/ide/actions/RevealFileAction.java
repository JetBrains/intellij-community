// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

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
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    e.getPresentation().setEnabledAndVisible(isSupported() && getFile(e) != null &&
                                             (!ActionPlaces.isPopupPlace(e.getPlace()) ||
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

  public static boolean isSupported() {
    return SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.hasXdgOpen() ||
           Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
  }

  public static @ActionText @NotNull String getActionName() {
    return getActionName(null);
  }

  public static @ActionText @NotNull String getActionName(@Nullable String place) {
    var shortName = ActionPlaces.REVEAL_IN_POPUP.equals(place);
    return shortName ? getFileManagerName() : getActionName(false);
  }

  private static @ActionText String getActionName(boolean skipDetection) {
    return SystemInfo.isMac ? ActionsBundle.message("action.RevealIn.name.mac") : ActionsBundle.message("action.RevealIn.name.other", getFileManagerName(skipDetection));
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
    return SystemInfo.isMac ? IdeBundle.message("action.finder.text") :
           SystemInfo.isWindows ? IdeBundle.message("action.explorer.text") :
           skipDetection ? IdeBundle.message("action.file.manager.text") :
           Objects.requireNonNullElseGet(Holder.fileManagerName, () -> IdeBundle.message("action.file.manager.text"));
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

  /** @see #openFile(Path) */
  public static void openFile(@NotNull File file) {
    openFile(file.toPath());
  }

  /**
   * Opens a system file manager with the given file's parent directory loaded and the file highlighted in it
   * (note that some platforms do not support the file highlighting).
   */
  public static void openFile(@NotNull Path file) {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      doOpen(parent, file);
    }
    else {
      doOpen(file, null);
    }
  }

  /** @see #openDirectory(Path) */
  public static void openDirectory(@NotNull File directory) {
    doOpen(directory.toPath(), null);
  }

  /**
   * Opens a system file manager with the given directory loaded in it.
   */
  public static void openDirectory(@NotNull Path directory) {
    doOpen(directory, null);
  }

  private static void doOpen(@NotNull Path _dir, @Nullable Path _toSelect) {
    String dir = _dir.toAbsolutePath().normalize().toString();
    String toSelect = _toSelect != null ? _toSelect.toAbsolutePath().normalize().toString() : null;
    String fmApp;

    if (SystemInfo.isWindows) {
      if (JnaLoader.isLoaded()) {
        openViaShellApi(dir, toSelect);
      }
      else {
        openViaExplorerCall(dir, toSelect);
      }
    }
    else if (SystemInfo.isMac) {
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
    else if (SystemInfo.hasXdgOpen()) {
      spawn("xdg-open", dir);
    }
    else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      if (LOG.isDebugEnabled()) LOG.debug("opening " + dir + " via Desktop API");
      ProcessIOExecutorService.INSTANCE.execute(() -> {
        try {
          Desktop.getDesktop().open(new File(dir));
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      });
    }
    else {
      Messages.showErrorDialog(IdeBundle.message("message.this.action.isn.t.supported.on.the.current.platform"),
                               IdeBundle.message("dialog.title.cannot.open.file"));
    }
  }

  private static void openViaShellApi(String dir, @Nullable String toSelect) {
    if (LOG.isDebugEnabled()) LOG.debug("shell open: dir=" + dir + " toSelect=" + toSelect);

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);

      var pIdl = Shell32Ex.INSTANCE.ILCreateFromPath(dir);
      var apIdl = toSelect != null ? new Pointer[]{Shell32Ex.INSTANCE.ILCreateFromPath(toSelect)} : null;
      var cIdl = new WinDef.UINT(apIdl != null ? apIdl.length : 0);
      try {
        var result = Shell32Ex.INSTANCE.SHOpenFolderAndSelectItems(pIdl, cIdl, apIdl, new WinDef.DWORD(0));
        if (!WinError.S_OK.equals(result)) {
          LOG.warn("SHOpenFolderAndSelectItems(" + dir + ',' + toSelect + "): 0x" + Integer.toHexString(result.intValue()));
          openViaExplorerCall(dir, toSelect);
        }
      }
      finally {
        if (apIdl != null) {
          Shell32Ex.INSTANCE.ILFree(apIdl[0]);
        }
        Shell32Ex.INSTANCE.ILFree(pIdl);
      }
    });
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
        CapturingProcessHandler handler;
        if (SystemInfo.isWindows) {
          assert command.length == 1 : Arrays.toString(command);
          Process process = Runtime.getRuntime().exec(command[0]);  // no quoting/escaping is needed
          handler = new CapturingProcessHandler.Silent(process, null, command[0]);
        }
        else {
          handler = new CapturingProcessHandler.Silent(new GeneralCommandLine(command));
        }
        handler.runProcess(10000, false).checkSuccess(LOG);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }

  private static final class Holder {
    private static final String[] supportedFileManagers = {"nautilus", "pantheon-files", "dolphin", "dde-file-manager"};

    private static final @Nullable String fileManagerApp;
    private static final @Nullable @NlsSafe String fileManagerName;

    static {
      String fmApp = null, fmName = null;
      if (SystemInfo.hasXdgMime()) {
        var desktopEntryName = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-mime", "query", "default", "inode/directory"));
        if (desktopEntryName != null && desktopEntryName.endsWith(".desktop")) {
          var desktopFile = Stream.of(getXdgDataDirectories().split(":"))
            .map(dir -> Path.of(dir, "applications", desktopEntryName))
            .filter(Files::exists)
            .findFirst();
          if (desktopFile.isPresent()) {
            try {
              var lines = Files.readAllLines(desktopFile.get());
              fmApp = lines.stream()
                .filter(line -> line.startsWith("Exec="))
                .map(line -> line.substring(5).split(" ")[0])
                .filter(app -> ContainerUtil.exists(supportedFileManagers, supportedFileManager -> app.endsWith(supportedFileManager)))
                .findFirst().orElse(null);
              fmName = lines.stream()
                .filter(line -> line.startsWith("Name="))
                .map(line -> line.substring(5))
                .findFirst().orElse(null);
            }
            catch (InvalidPathException | IOException e) {
              LOG.error(e);
            }
          }
        }
      }
      fileManagerApp = fmApp;
      fileManagerName = fmName;
    }

    private static String getXdgDataDirectories() {
      return StringUtil.defaultIfEmpty(System.getenv("XDG_DATA_HOME"), SystemProperties.getUserHome() + "/.local/share") + ':' +
             StringUtil.defaultIfEmpty(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated trivial to implement, just inline */
  @Deprecated(forRemoval = true)
  public static void showDialog(Project project,
                                @NlsContexts.DialogMessage String message,
                                @NlsContexts.DialogTitle String title,
                                @NotNull File file,
                                @SuppressWarnings("removal") @Nullable DialogWrapper.DoNotAskOption option) {
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

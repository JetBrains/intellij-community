// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.SystemProperties;
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
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This helpful action opens a file or directory in a system file manager.
 *
 * @see ShowFilePathAction
 */
public class RevealFileAction extends DumbAwareAction implements LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(RevealFileAction.class);

  public static final NotificationListener FILE_SELECTING_LISTENER = new NotificationListener.Adapter() {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      URL url = e.getURL();
      if (url != null) {
        try {
          openFile(new File(url.toURI()));
        }
        catch (URISyntaxException ex) {
          LOG.warn("invalid URL: " + url, ex);
        }
      }
      notification.hideBalloon();
    }
  };

  public RevealFileAction() {
    getTemplatePresentation().setText(getActionName(null));
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
    VirtualFile file = getFile(e);
    if (file != null) {
      openFile(new File(file.getPresentableUrl()));
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
    if (ActionPlaces.EDITOR_TAB_POPUP.equals(place) || ActionPlaces.EDITOR_POPUP.equals(place) || ActionPlaces.PROJECT_VIEW_POPUP.equals(place)) {
      return getFileManagerName();
    }
    else if (SystemInfo.isMac) {
      return ActionsBundle.message("action.RevealIn.name.mac");
    }
    else {
      return ActionsBundle.message("action.RevealIn.name.other", getFileManagerName());
    }
  }

  public static @NlsSafe @NotNull String getFileManagerName() {
    return Holder.fileManagerName;
  }

  public static @Nullable VirtualFile findLocalFile(@Nullable VirtualFile file) {
    if (file == null || file.isInLocalFileSystem()) {
      return file;
    }

    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof ArchiveFileSystem && file.getParent() == null) {
      return ((ArchiveFileSystem)fs).getLocalByEntry(file);
    }

    return null;
  }

  /**
   * Opens a system file manager with the given file's parent directory open and the file highlighted in it
   * (note that not all platforms support highlighting).
   */
  public static void openFile(@NotNull File file) {
    openFile(file.toPath());
  }

  /**
   * Opens a system file manager with the given file's parent directory open and the file highlighted in it
   * (note that not all platforms support highlighting).
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

  /**
   * Opens a system file manager with the given directory open in it.
   */
  public static void openDirectory(@NotNull File directory) {
    doOpen(directory.toPath(), null);
  }

  /**
   * Opens a system file manager with the given directory open in it.
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
        spawn(toSelect != null ? "explorer /select,\"" + toSelect + '"' : "explorer /root,\"" + dir + '"');
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
      if (fmApp.endsWith("dolphin") && toSelect != null) {
        spawn(fmApp, "--select", toSelect);
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

  private static void openViaShellApi(String dir, String toSelect) {
    if (LOG.isDebugEnabled()) LOG.debug("shell open: dir=" + dir + " toSelect=" + toSelect);

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      Pointer pIdl = Shell32Ex.INSTANCE.ILCreateFromPath(dir);
      Pointer[] apIdl = toSelect != null ? new Pointer[]{Shell32Ex.INSTANCE.ILCreateFromPath(toSelect)} : null;
      WinDef.UINT cIdl = new WinDef.UINT(apIdl != null ? apIdl.length : 0);
      try {
        WinNT.HRESULT result = Shell32Ex.INSTANCE.SHOpenFolderAndSelectItems(pIdl, cIdl, apIdl, new WinDef.DWORD(0));
        if (!WinError.S_OK.equals(result)) {
          LOG.warn("SHOpenFolderAndSelectItems(" + dir + ',' + toSelect + "): 0x" + Integer.toHexString(result.intValue()));
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

  private interface Shell32Ex extends StdCallLibrary {
    Shell32Ex INSTANCE = init();

    private static Shell32Ex init() {
      Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
      return Native.load("shell32", Shell32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
    }

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

  private static class Holder {
    private static final String fileManagerApp =
      readDesktopEntryKey("Exec")
        .map(line -> line.split(" ")[0])
        .filter(exec -> exec.endsWith("nautilus") || exec.endsWith("pantheon-files") || exec.endsWith("dolphin"))
        .orElse(null);

    private static final String fileManagerName =
      SystemInfo.isMac ? "Finder" :
      SystemInfo.isWindows ? "Explorer" :
      readDesktopEntryKey("Name").orElse("File Manager");

    private static Optional<String> readDesktopEntryKey(String key) {
      if (SystemInfo.hasXdgMime()) {
        String appName = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-mime", "query", "default", "inode/directory"));
        if (appName != null && appName.endsWith(".desktop")) {
          return Stream.of(getXdgDataDirectories().split(":"))
            .map(dir -> new File(dir, "applications/" + appName))
            .filter(File::exists)
            .findFirst()
            .map(file -> readDesktopEntryKey(file, key));
        }
      }

      return Optional.empty();
    }

    private static String getXdgDataDirectories() {
      return StringUtil.defaultIfEmpty(System.getenv("XDG_DATA_HOME"), SystemProperties.getUserHome() + "/.local/share") + ':' +
             StringUtil.defaultIfEmpty(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
    }

    private static String readDesktopEntryKey(File file, String key) {
      if (LOG.isDebugEnabled()) LOG.debug("looking for '" + key + "' in " + file);
      String prefix = key + '=';
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
        return reader.lines().filter(l -> l.startsWith(prefix)).map(l -> l.substring(prefix.length())).findFirst().orElse(null);
      }
      catch (IOException | UncheckedIOException e) {
        LOG.info("Cannot read: " + file, e);
        return null;
      }
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

  /** @deprecated pointless; please use {@link #openFile} instead */
  @Deprecated(forRemoval = true)
  public static void selectDirectory(@NotNull File directory) {
    openFile(directory);
  }
  //</editor-fold>
}

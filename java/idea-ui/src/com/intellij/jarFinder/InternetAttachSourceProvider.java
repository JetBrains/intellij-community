package com.intellij.jarFinder;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class InternetAttachSourceProvider implements AttachSourcesProvider {

  private static final Logger LOG = Logger.getInstance("#com.intellij.jarFinder.SonatypeAttachSourceProvider");

  private static final Pattern ARTIFACT_IDENTIFIER = Pattern.compile("[A-Za-z0-9\\.\\-_]+");

  @Nullable
  protected static VirtualFile getJarByPsiFile(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;

    VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(psiFile.getVirtualFile());

    if (jar == null || !jar.getName().endsWith(".jar")) return null;

    return jar;
  }

  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return Collections.emptyList();

    final String jarName = jar.getNameWithoutExtension();
    int index = jarName.lastIndexOf('-');
    if (index == -1) return Collections.emptyList();

    final String version = jarName.substring(index + 1);
    final String artifactId = jarName.substring(0, index);

    if (!ARTIFACT_IDENTIFIER.matcher(version).matches() || !ARTIFACT_IDENTIFIER.matcher(artifactId).matches()) {
      return Collections.emptyList();
    }

    final Set<Library> libraries = new HashSet<Library>();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
    }

    if (libraries.isEmpty()) return Collections.emptyList();

    final String sourceFileName = jarName + "-sources.jar";

    for (Library library : libraries) {
      for (VirtualFile file : library.getFiles(OrderRootType.SOURCES)) {
        if (file.getPath().contains(sourceFileName)) {
          if (isRootInExistingFile(file)) {
            return Collections.emptyList(); // Sources already attached, but source-jar doesn't contain current class.
          }
        }
      }
    }

    final File libSourceDir = getLibrarySourceDir();

    final File sourceFile = new File(libSourceDir, sourceFileName);

    if (sourceFile.exists()) {
      return Collections.<AttachSourcesAction>singleton(new LightAttachSourcesAction() {
        @Override
        public String getName() {
          return "Attach downloaded source";
        }

        @Override
        public String getBusyText() {
          return getName();
        }

        @Override
        public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
          attachSourceJar(sourceFile, libraries);
          return new ActionCallback.Done();
        }
      });
    }

    return Collections.<AttachSourcesAction>singleton(new LightAttachSourcesAction() {
      @Override
      public String getName() {
        return "Search in internet...";
      }

      @Override
      public String getBusyText() {
        return "Searching...";
      }

      @Override
      public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
        final Task task = new Task.Modal(psiFile.getProject(), "Searching source...", true) {

          // Don't move initialization of searchers to static context of top level class, to avoid unnecessary initialization of searcher's classes
          private SourceSearcher[] mySearchers = new SourceSearcher[]{new MavenCentralSourceSearcher(), new SonatypeSourceSearcher()};

          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            String artifactUrl = null;

            for (SourceSearcher searcher : mySearchers) {
              try {
                artifactUrl = searcher.findSourceJar(indicator, artifactId, version);
              }
              catch (SourceSearchException e) {
                showMessage("Downloading failed", e.getMessage(), NotificationType.ERROR);
                continue;
              }

              if (artifactUrl != null) break;
            }

            if (artifactUrl == null) {
              showMessage("Source not found", "Sources for: " + jarName + ".jar not found", NotificationType.WARNING);
              return;
            }

            libSourceDir.mkdirs();

            if (!libSourceDir.exists()) {
              showMessage("Downloading failed", "Failed to create directory to store sources: " + libSourceDir, NotificationType.ERROR);
              return;
            }

            try {
              HttpURLConnection urlConnection = HttpConfigurable.getInstance().openHttpConnection(artifactUrl);

              int contentLength = urlConnection.getContentLength();

              File tmpDownload = File.createTempFile("download", ".tmp", libSourceDir);
              OutputStream out = new BufferedOutputStream(new FileOutputStream(tmpDownload));

              try {
                InputStream in = urlConnection.getInputStream();
                indicator.setText("Downloading sources...");
                indicator.setIndeterminate(false);
                try {
                  NetUtils.copyStreamContent(indicator, in, out, contentLength);
                }
                finally {
                  in.close();
                }
              }
              finally {
                out.close();
              }

              if (!sourceFile.exists()) {
                if (!tmpDownload.renameTo(sourceFile)) {
                  LOG.warn("Failed to rename file " + tmpDownload + " to " + sourceFileName);
                }
              }
            }
            catch (IOException e) {
              showMessage("Downloading failed", "Connection problem. See log for more details.", NotificationType.ERROR);
            }
          }

          @Override
          public void onSuccess() {
            attachSourceJar(sourceFile, libraries);
          }

          private void showMessage(final String title, final String message, final NotificationType notificationType) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                new Notification("Source searcher",
                                 title,
                                 message,
                                 notificationType)
                  .notify(getProject());
              }
            });
          }
        };

        task.queue();

        return new ActionCallback.Done();
      }
    });
  }

  private static boolean isRootInExistingFile(VirtualFile root) {
    if (root.getFileSystem() instanceof JarFileSystem) {
      VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(root);
      if (jar == null) return false;

      jar.refresh(false, false);

      return root.isValid();
    }

    return true;
  }

  public static void attachSourceJar(@NotNull File sourceJar, @NotNull Collection<Library> libraries) {
    AccessToken accessToken = WriteAction.start();

    try {
      VirtualFile srcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceJar);
      if (srcFile == null) return;

      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
      if (jarRoot == null) return;

      VirtualFile[] roots = PathUIUtils.scanAndSelectDetectedJavaSourceRoots(null, new VirtualFile[]{jarRoot});
      if (roots.length == 0) {
        roots = new VirtualFile[]{jarRoot};
      }

      for (Library library : libraries) {
        Library.ModifiableModel model = library.getModifiableModel();
        List<VirtualFile> alreadyExistingFiles = Arrays.asList(model.getFiles(OrderRootType.SOURCES));

        for (VirtualFile root : roots) {
          if (!alreadyExistingFiles.contains(root)) {
            model.addRoot(root, OrderRootType.SOURCES);
          }
        }
        model.commit();
      }
    }
    finally {
      accessToken.finish();
    }
  }

  public static File getLibrarySourceDir() {
    String path = System.getProperty("idea.library.source.dir");
    if (path != null) {
      return new File(path);
    }

    return new File(SystemProperties.getUserHome(), ".ideaLibSources");
  }
}

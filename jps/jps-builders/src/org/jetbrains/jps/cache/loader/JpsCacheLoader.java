//package org.jetbrains.jps.cache.loader;
//
//import com.intellij.compiler.server.BuildManager;
//import com.intellij.jps.cache.JpsCacheBundle;
//import com.intellij.jps.cache.client.JpsServerClient;
//import com.intellij.jps.cache.loader.JpsOutputLoader;
//import com.intellij.jps.cache.model.JpsLoaderContext;
//import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.progress.ProcessCanceledException;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.util.io.FileUtil;
//import com.intellij.util.io.ZipUtil;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.jps.cache.JpsCacheBundle;
//import org.jetbrains.jps.cache.client.JpsServerClient;
//import org.jetbrains.jps.cache.model.JpsLoaderContext;
//import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;
//
//import java.io.File;
//import java.io.IOException;
//
//class JpsCacheLoader implements JpsOutputLoader<File> {
//  private static final Logger LOG = Logger.getInstance(JpsCacheLoader.class);
//  private static final String TIMESTAMPS_FOLDER_NAME = "timestamps";
//  private static final String FS_STATE_FILE = "fs_state.dat";
//  private final BuildManager myBuildManager;
//  private final JpsServerClient myClient;
//  private final Project myProject;
//  private File myTmpCacheFolder;
//
//  JpsCacheLoader(JpsServerClient client, @NotNull Project project) {
//    myBuildManager = BuildManager.getInstance();
//    myClient = client;
//    myProject = project;
//  }
//
//  @Nullable
//  @Override
//  public File load(@NotNull JpsLoaderContext context) {
//    LOG.info("Loading JPS caches for commit: " + context.getCommitId());
//    myTmpCacheFolder = null;
//
//    long start = System.currentTimeMillis();
//    File zipFile = myClient.downloadCacheById(context.getDownloadIndicatorManager(), context.getCommitId(),
//                                              myBuildManager.getBuildSystemDirectory(myProject).toFile());
//    LOG.info("Download of jps caches took: " + (System.currentTimeMillis() - start));
//    return zipFile;
//  }
//
//  @Override
//  public LoaderStatus extract(@Nullable Object loadResults, @NotNull SegmentedProgressIndicatorManager extractIndicatorManager) {
//    if (!(loadResults instanceof File)) return LoaderStatus.FAILED;
//
//    File zipFile = (File)loadResults;
//    File targetDir = myBuildManager.getBuildSystemDirectory(myProject).toFile();
//    File tmpFolder = new File(targetDir, "tmp");
//    try {
//      // Start extracting after download
//      SegmentedProgressIndicatorManager.SubTaskProgressIndicator subTaskIndicator = extractIndicatorManager.createSubTaskIndicator();
//      extractIndicatorManager.getProgressIndicator().checkCanceled();
//      extractIndicatorManager.setText(this, JpsCacheBundle.message("progress.text.extracting.downloaded.results"));
//      subTaskIndicator.setText2(JpsCacheBundle.message("progress.details.extracting.project.caches"));
//      long start = System.currentTimeMillis();
//
//      ZipUtil.extract(zipFile, tmpFolder, null);
//      FileUtil.delete(zipFile);
//      LOG.info("Unzip compilation caches took: " + (System.currentTimeMillis() - start));
//      subTaskIndicator.finished();
//      extractIndicatorManager.finished(this);
//
//      myTmpCacheFolder = tmpFolder;
//      return LoaderStatus.COMPLETE;
//    }
//    catch (ProcessCanceledException | IOException e) {
//      if (e instanceof IOException) LOG.warn("Failed unzip downloaded compilation caches", e);
//      FileUtil.delete(zipFile);
//      FileUtil.delete(tmpFolder);
//    }
//    return LoaderStatus.FAILED;
//  }
//
//  @Override
//  public void rollback() {
//    if (myTmpCacheFolder != null && myTmpCacheFolder.exists()) {
//      FileUtil.delete(myTmpCacheFolder);
//      LOG.debug("JPS cache loader rolled back");
//    }
//  }
//
//  @Override
//  public void apply(@NotNull SegmentedProgressIndicatorManager indicatorManager) {
//    if (myTmpCacheFolder == null) {
//      LOG.warn("Nothing to apply, download results are empty");
//      return;
//    }
//
//    File newTimestampFolder = new File(myTmpCacheFolder, TIMESTAMPS_FOLDER_NAME);
//    if (newTimestampFolder.exists()) FileUtil.delete(newTimestampFolder);
//
//    File currentDirForBuildCache = myBuildManager.getProjectSystemDirectory(myProject);
//    indicatorManager.setText(this, JpsCacheBundle.message("progress.text.applying.jps.caches"));
//    if (currentDirForBuildCache != null) {
//      SegmentedProgressIndicatorManager.SubTaskProgressIndicator subTaskIndicator = indicatorManager.createSubTaskIndicator();
//      subTaskIndicator.setText2(JpsCacheBundle.message("progress.details.applying.downloaded.caches"));
//      // Copy timestamp old folder to new cache dir
//      File timestamps = new File(currentDirForBuildCache, TIMESTAMPS_FOLDER_NAME);
//      if (timestamps.exists()) {
//        try {
//          newTimestampFolder.mkdirs();
//          FileUtil.copyDir(timestamps, newTimestampFolder);
//        }
//        catch (IOException e) {
//          LOG.warn("Couldn't copy timestamps from old JPS cache", e);
//        }
//      }
//
//      // Create new empty fsStateFile
//      File fsStateFile = new File(myTmpCacheFolder, FS_STATE_FILE);
//      fsStateFile.delete();
//      try {
//        fsStateFile.createNewFile();
//      }
//      catch (IOException e) {
//        LOG.warn("Couldn't create new empty FsState file", e);
//      }
//
//      // Remove old cache dir
//      FileUtil.delete(currentDirForBuildCache);
//      myTmpCacheFolder.renameTo(currentDirForBuildCache);
//      subTaskIndicator.finished();
//      LOG.debug("JPS cache downloads finished");
//    }
//    indicatorManager.finished(this);
//  }
//}
package org.jetbrains.jps.cache.loader;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.model.OutputLoadResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.jetbrains.jps.cache.JpsCachesLoaderUtil.EXECUTOR_SERVICE;

class JpsCompilationOutputLoader implements JpsOutputLoader<List<OutputLoadResult>> {
  private static final Logger LOG = Logger.getInstance(JpsCompilationOutputLoader.class);
  private static final String RESOURCES_PRODUCTION = "resources-production";
  private static final String JAVA_PRODUCTION = "java-production";
  private static final String RESOURCES_TEST = "resources-test";
  private static final String JAVA_TEST = "java-test";
  private static final String PRODUCTION = "production";
  private static final String TEST = "test";
  private final JpsServerClient myClient;
  private final String myBuildDirPath;
  private Map<File, String> myTmpFolderToModuleName;
  private List<File> myOldModulesPaths;
  private JpsLoaderContext myContext;

  JpsCompilationOutputLoader(@NotNull JpsServerClient client, @NotNull String buildDirPath) {
    myClient = client;
    myBuildDirPath = buildDirPath;
  }

  @Override
  public int calculateDownloads(@NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return calculateAffectedModules(currentSourcesState, commitSourcesState, true).size();
  }

  @Override
  public List<OutputLoadResult> load() {
    myOldModulesPaths = null;
    myTmpFolderToModuleName = null;

    myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.calculating.affected.modules"));
    List<AffectedModule> affectedModules = calculateAffectedModules(myContext.getCurrentSourcesState(),
                                                                    myContext.getCommitSourcesState(), true);
    myContext.checkCanceled();
    if (affectedModules.size() > 0) {
      long start = System.currentTimeMillis();
      List<OutputLoadResult> loadResults = myClient.downloadCompiledModules(myContext, affectedModules);
      LOG.info("Download of compilation outputs took: " + (System.currentTimeMillis() - start));
      return loadResults;
    }
    return Collections.emptyList();
  }

  @Override
  public LoaderStatus extract(@Nullable Object loadResults) {
    if (!(loadResults instanceof List)) return LoaderStatus.FAILED;
    LOG.info("Start extraction of compilation outputs");

    //noinspection unchecked
    List<OutputLoadResult> outputLoadResults = (List<OutputLoadResult>)loadResults;
    Map<File, String> result = new ConcurrentHashMap<>();
    try {
      // Extracting results
      long start = System.currentTimeMillis();
      myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.extracting.downloaded.results"));
      List<Future<?>> futureList = ContainerUtil.map(outputLoadResults, loadResult ->
        EXECUTOR_SERVICE.submit(new UnzipOutputTask(result, loadResult, myContext)));
      for (Future<?> future : futureList) {
        future.get();
      }
      myTmpFolderToModuleName = result;
      LOG.info("Unzip compilation output took: " + (System.currentTimeMillis() - start));
      return LoaderStatus.COMPLETE;
    }
    catch (ProcessCanceledException | InterruptedException | ExecutionException e) {
      if (!(e.getCause() instanceof ProcessCanceledException)) LOG.warn("Failed unzip downloaded compilation outputs", e);
      outputLoadResults.forEach(loadResult -> FileUtil.delete(loadResult.getZipFile()));
      result.forEach((key, value) -> FileUtil.delete(key));
    }
    return LoaderStatus.FAILED;
  }

  @Override
  public void rollback() {
    if (myTmpFolderToModuleName == null) return;
    myTmpFolderToModuleName.forEach((tmpFolder, __) -> {
      if (tmpFolder.isDirectory() && tmpFolder.exists()) FileUtil.delete(tmpFolder);
    });
    LOG.info("JPS cache loader rolled back");
  }

  @Override
  public void apply() {
    long start = System.currentTimeMillis();
    if (myOldModulesPaths != null) {
      LOG.info("Removing old compilation outputs " + myOldModulesPaths.size() + " counts");
      myOldModulesPaths.forEach(file -> {
        if (file.exists()) FileUtil.delete(file);
      });
    }
    if (myTmpFolderToModuleName == null) {
      LOG.debug("Nothing to apply, download results are empty");
      return;
    }

    myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.applying.jps.caches"));
    ContainerUtil.map(myTmpFolderToModuleName.entrySet(),
                      entry -> EXECUTOR_SERVICE.submit(() -> {
                        String moduleName = entry.getValue();
                        File tmpModuleFolder = entry.getKey();
                        myContext.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.details.applying.changes.for.module", moduleName));
                        File currentModuleBuildDir = new File(tmpModuleFolder.getParentFile(), moduleName);
                        FileUtil.delete(currentModuleBuildDir);
                        try {
                          FileUtil.rename(tmpModuleFolder, currentModuleBuildDir);
                          LOG.debug("Module: " + moduleName + " was replaced successfully");
                        }
                        catch (IOException e) {
                          LOG.warn("Couldn't replace compilation output for module: " + moduleName, e);
                        }
                      }))
      .forEach(future -> {
        try {
          future.get();
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.info("Couldn't apply compilation output", e);
        }
      });
    LOG.info("Applying compilation output took: " + (System.currentTimeMillis() - start));
  }

  @Override
  public void setContext(@NotNull JpsLoaderContext context) {
    myContext = context;
  }

  @NotNull
  private List<AffectedModule> calculateAffectedModules(@Nullable Map<String, Map<String, BuildTargetState>> currentModulesState,
                                                        @NotNull Map<String, Map<String, BuildTargetState>> commitModulesState,
                                                        boolean checkExistance) {
    long start = System.currentTimeMillis();

    List<AffectedModule> affectedModules = new ArrayList<>();
    Map<String, String> oldModulesMap = new HashMap<>();
    myOldModulesPaths = new ArrayList<>();

    if (currentModulesState == null) {
      commitModulesState.forEach((type, map) -> {
        map.forEach((name, state) -> {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), getBuildDirRelativeFile(state.getRelativePath())));
        });
      });
      LOG.warn("Project doesn't contain metadata, force to download " + affectedModules.size() + " modules.");
      List<AffectedModule> result = mergeAffectedModules(affectedModules, commitModulesState);
      long total = System.currentTimeMillis() - start;
      LOG.info("Compilation output affected for the " + result.size() + " modules. Computation took " + total + "ms");
      return result;
    }

    // Add new build types
    Set<String> newBuildTypes = new HashSet<>(commitModulesState.keySet());
    newBuildTypes.removeAll(currentModulesState.keySet());
    newBuildTypes.forEach(type -> {
      commitModulesState.get(type).forEach((name, state) -> {
        affectedModules.add(new AffectedModule(type, name, state.getHash(), getBuildDirRelativeFile(state.getRelativePath())));
      });
    });

    // Calculate old paths for remove
    Set<String> oldBuildTypes = new HashSet<>(currentModulesState.keySet());
    oldBuildTypes.removeAll(commitModulesState.keySet());
    oldBuildTypes.forEach(type -> {
      currentModulesState.get(type).forEach((name, state) -> {
        oldModulesMap.put(name, state.getRelativePath());
      });
    });

    commitModulesState.forEach((type, map) -> {
      Map<String, BuildTargetState> currentTypeState = currentModulesState.get(type);

      // New build type already added above
      if (currentTypeState == null) return;

      // Add new build modules
      Set<String> newBuildModules = new HashSet<>(map.keySet());
      newBuildModules.removeAll(currentTypeState.keySet());
      newBuildModules.forEach(name -> {
        BuildTargetState state = map.get(name);
        affectedModules.add(new AffectedModule(type, name, state.getHash(), getBuildDirRelativeFile(state.getRelativePath())));
      });

      // Calculate old modules paths for remove
      Set<String> oldBuildModules = new HashSet<>(currentTypeState.keySet());
      oldBuildModules.removeAll(map.keySet());
      oldBuildModules.forEach(name -> {
        BuildTargetState state = currentTypeState.get(name);
        oldModulesMap.put(name, state.getRelativePath());
      });

      // In another case compare modules inside the same build type
      map.forEach((name, state) -> {
        BuildTargetState currentTargetState = currentTypeState.get(name);
        if (currentTargetState == null || !state.equals(currentTargetState)) {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), getBuildDirRelativeFile(state.getRelativePath())));
          return;
        }

        File outFile = getBuildDirRelativeFile(state.getRelativePath());
        if (checkExistance && (!outFile.exists() || ArrayUtil.isEmpty(outFile.listFiles()))) {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), outFile));
        }
      });
    });

    // Check that old modules not exist in other build types
    myOldModulesPaths = oldModulesMap.entrySet().stream().filter(entry -> {
      for (Map.Entry<String, Map<String, BuildTargetState>> commitEntry : commitModulesState.entrySet()) {
        BuildTargetState targetState = commitEntry.getValue().get(entry.getKey());
        if (targetState != null && targetState.getRelativePath().equals(entry.getValue())) return false;
      }
      return true;
    }).map(entry -> getBuildDirRelativeFile(entry.getValue()))
      .collect(Collectors.toList());
    List<AffectedModule> result = mergeAffectedModules(affectedModules, commitModulesState);
    long total = System.currentTimeMillis() - start;
    LOG.info("Compilation output affected for the " + result.size() + " modules. Computation took " + total + "ms");
    return result;
  }

  @NotNull
  private static List<AffectedModule> mergeAffectedModules(List<AffectedModule> affectedModules,
                                                           @NotNull Map<String, Map<String, BuildTargetState>> commitModulesState) {
    Set<AffectedModule> result = new HashSet<>();
    affectedModules.forEach(affectedModule -> {
      if (affectedModule.getType().equals(JAVA_PRODUCTION)) {
        BuildTargetState targetState = commitModulesState.get(RESOURCES_PRODUCTION).get(affectedModule.getName());
        if (targetState == null) {
          result.add(affectedModule);
          return;
        }
        String hash = calculateStringHash(affectedModule.getHash() + targetState.getHash());
        result.add(new AffectedModule(PRODUCTION, affectedModule.getName(), hash, affectedModule.getOutPath()));
      }
      else if (affectedModule.getType().equals(RESOURCES_PRODUCTION)) {
        BuildTargetState targetState = commitModulesState.get(JAVA_PRODUCTION).get(affectedModule.getName());
        if (targetState == null) {
          result.add(affectedModule);
          return;
        }
        String hash = calculateStringHash(targetState.getHash() + affectedModule.getHash());
        result.add(new AffectedModule(PRODUCTION, affectedModule.getName(), hash, affectedModule.getOutPath()));
      }
      else if (affectedModule.getType().equals(JAVA_TEST)) {
        BuildTargetState targetState = commitModulesState.get(RESOURCES_TEST).get(affectedModule.getName());
        if (targetState == null) {
          result.add(affectedModule);
          return;
        }
        String hash = calculateStringHash(affectedModule.getHash() + targetState.getHash());
        result.add(new AffectedModule(TEST, affectedModule.getName(), hash, affectedModule.getOutPath()));
      }
      else if (affectedModule.getType().equals(RESOURCES_TEST)) {
        BuildTargetState targetState = commitModulesState.get(JAVA_TEST).get(affectedModule.getName());
        if (targetState == null) {
          result.add(affectedModule);
          return;
        }
        String hash = calculateStringHash(targetState.getHash() + affectedModule.getHash());
        result.add(new AffectedModule(TEST, affectedModule.getName(), hash, affectedModule.getOutPath()));
      }
      else {
        result.add(affectedModule);
      }
    });
    return new ArrayList<>(result);
  }

  private File getBuildDirRelativeFile(String buildDirRelativePath) {
    return new File(buildDirRelativePath.replace("$BUILD_DIR$", myBuildDirPath));
  }

  private static String calculateStringHash(String content) {
    Hasher hasher = Hashing.murmur3_128().newHasher();
    return hasher.putString(content, StandardCharsets.UTF_8).hash().toString();
  }

  @TestOnly
  List<File> getOldModulesPaths() {
    return myOldModulesPaths;
  }

  @TestOnly
  List<AffectedModule> getAffectedModules(@Nullable Map<String, Map<String, BuildTargetState>> currentModulesState,
                                          @NotNull Map<String, Map<String, BuildTargetState>> commitModulesState, boolean checkExistence) {
    return calculateAffectedModules(currentModulesState, commitModulesState, checkExistence);
  }

  private static final class UnzipOutputTask implements Runnable {
    private final OutputLoadResult loadResult;
    private final Map<File, String> result;
    private final JpsLoaderContext context;

    private UnzipOutputTask(Map<File, String> result,
                            OutputLoadResult loadResult,
                            JpsLoaderContext context) {
      this.result = result;
      this.loadResult = loadResult;
      this.context = context;
    }

    @Override
    public void run() {
      AffectedModule affectedModule = loadResult.getModule();
      File outPath = affectedModule.getOutPath();
      try {
        context.checkCanceled();
        int expectedDownloads = context.getTotalExpectedDownloads();
        context.getNettyClient().sendDescriptionStatusMessage(JpsBuildBundle.message("progress.details.extracting.compilation.outputs.for.module", affectedModule.getName()), expectedDownloads);
        LOG.debug("Downloaded JPS compiled module from: " + loadResult.getDownloadUrl());
        File tmpFolder = new File(outPath.getParent(), outPath.getName() + "_tmp");
        File zipFile = loadResult.getZipFile();
        ZipUtil.extract(zipFile, tmpFolder, null);
        FileUtil.delete(zipFile);
        result.put(tmpFolder, affectedModule.getName());
        //subTaskIndicator.finished();
      }
      catch (IOException e) {
        LOG.warn("Couldn't extract download result for module: " + affectedModule.getName(), e);
      }
    }
  }
}
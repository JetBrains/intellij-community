// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.target.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LocalTargetEnvironment extends TargetEnvironment implements TargetEnvironment.PtyTargetEnvironment {
  private final Map<UploadRoot, UploadableVolume> myUploadVolumes = new HashMap<>();
  private final Map<DownloadRoot, DownloadableVolume> myDownloadVolumes = new HashMap<>();
  private final Map<TargetPortBinding, Integer> myTargetPortBindings = new HashMap<>();
  private final Map<LocalPortBinding, ResolvedPortBinding> myLocalPortBindings = new HashMap<>();

  public LocalTargetEnvironment(@NotNull LocalTargetEnvironmentRequest request) {
    super(request);

    for (UploadRoot uploadRoot : request.getUploadVolumes()) {
      final Path targetRoot;
      if (uploadRoot.getTargetRootPath() instanceof TargetPath.Persistent) {
        targetRoot = Paths.get(((TargetPath.Persistent)uploadRoot.getTargetRootPath()).getAbsolutePath());
      }
      else {
        targetRoot = uploadRoot.getLocalRootPath().toAbsolutePath();
      }
      myUploadVolumes.put(uploadRoot, new LocalVolume(uploadRoot.getLocalRootPath(), targetRoot));
    }

    for (DownloadRoot downloadRoot : request.getDownloadVolumes()) {
      final Path localRoot;
      if (downloadRoot.getLocalRootPath() != null) {
        localRoot = downloadRoot.getLocalRootPath();
      }
      else {
        try {
          localRoot = FileUtil.createTempDirectory("intellij-target", "", true).toPath();
        }
        catch (IOException err) {
          // TODO add `throws` to the methods signature, maybe convert to the factory.
          throw new IllegalStateException(err);
        }
      }
      final Path targetRoot;
      if (downloadRoot.getTargetRootPath() instanceof TargetPath.Persistent) {
        targetRoot = Paths.get(((TargetPath.Persistent)downloadRoot.getTargetRootPath()).getAbsolutePath());
      }
      else {
        targetRoot = localRoot;
      }
      myDownloadVolumes.put(downloadRoot, new LocalVolume(localRoot, targetRoot));
    }

    for (TargetPortBinding targetPortBinding : request.getTargetPortBindings()) {
      int theOnlyPort = targetPortBinding.getTarget();
      if (targetPortBinding.getLocal() != null && !targetPortBinding.getLocal().equals(theOnlyPort)) {
        throw new UnsupportedOperationException("Local target's TCP port forwarder is not implemented");
      }
      myTargetPortBindings.put(targetPortBinding, theOnlyPort);
    }

    for (LocalPortBinding localPortBinding : request.getLocalPortBindings()) {
      int theOnlyPort = localPortBinding.getLocal();
      if (localPortBinding.getTarget() != null && !localPortBinding.getTarget().equals(theOnlyPort)) {
        throw new UnsupportedOperationException("Local target's TCP port forwarder is not implemented");
      }
      myLocalPortBindings.put(localPortBinding, getResolvedPortBinding(theOnlyPort));
    }
  }

  @Override
  public @NotNull LocalTargetEnvironmentRequest getRequest() {
    return (LocalTargetEnvironmentRequest)super.getRequest();
  }

  @Override
  public boolean isWithPty() {
    return getRequest().getPtyOptions() != null;
  }

  private static @NotNull ResolvedPortBinding getResolvedPortBinding(int port) {
    HostPort hostPort = new HostPort("127.0.0.1", port);
    return new ResolvedPortBinding(hostPort, hostPort);
  }

  @NotNull
  @Override
  public Map<UploadRoot, UploadableVolume> getUploadVolumes() {
    return Collections.unmodifiableMap(myUploadVolumes);
  }

  @NotNull
  @Override
  public Map<DownloadRoot, DownloadableVolume> getDownloadVolumes() {
    return Collections.unmodifiableMap(myDownloadVolumes);
  }

  @NotNull
  @Override
  public Map<TargetPortBinding, Integer> getTargetPortBindings() {
    return Collections.unmodifiableMap(myTargetPortBindings);
  }

  @NotNull
  @Override
  public Map<LocalPortBinding, ResolvedPortBinding> getLocalPortBindings() {
    return Collections.unmodifiableMap(myLocalPortBindings);
  }

  @NotNull
  @Override
  public TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @NotNull
  @Override
  public Process createProcess(@NotNull TargetedCommandLine commandLine, @NotNull ProgressIndicator indicator) throws ExecutionException {
    return createGeneralCommandLine(commandLine).createProcess();
  }

  @NotNull
  public GeneralCommandLine createGeneralCommandLine(@NotNull TargetedCommandLine commandLine) throws CantRunException {
    try {
      LocalPtyOptions ptyOptions = getRequest().getPtyOptions();
      GeneralCommandLine generalCommandLine;
      if (ptyOptions != null) {
        PtyCommandLine ptyCommandLine = new PtyCommandLine(commandLine.collectCommandsSynchronously());
        ptyCommandLine.withOptions(ptyOptions);
        generalCommandLine = ptyCommandLine;
      }
      else {
        generalCommandLine = new GeneralCommandLine(commandLine.collectCommandsSynchronously());
      }
      generalCommandLine.withParentEnvironmentType(getRequest().getParentEnvironmentType());
      String inputFilePath = commandLine.getInputFilePath();
      if (inputFilePath != null) {
        generalCommandLine.withInput(new File(inputFilePath));
      }
      generalCommandLine.withCharset(commandLine.getCharset());
      String workingDirectory = commandLine.getWorkingDirectory();
      if (workingDirectory != null) {
        generalCommandLine.withWorkDirectory(workingDirectory);
      }
      generalCommandLine.withEnvironment(commandLine.getEnvironmentVariables());
      generalCommandLine.setRedirectErrorStream(commandLine.isRedirectErrorStream());
      return generalCommandLine;
    }
    catch (ExecutionException e) {
      throw new CantRunException(e.getMessage(), e);
    }
  }

  @Override
  public void shutdown() {
    //
  }

  private static final class LocalVolume implements UploadableVolume, DownloadableVolume {
    private final Path myLocalRoot;
    private final Path myTargetRoot;
    private final boolean myReal;

    private LocalVolume(@NotNull Path localRoot, @NotNull Path targetRoot) {
      myLocalRoot = localRoot;
      myTargetRoot = targetRoot;
      // Checking for local root existence is a workaround for tests like JavaCommandLineTest that check paths of imaginary files.
      boolean real;
      try {
        real = Files.exists(localRoot) &&
               !myLocalRoot.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(myTargetRoot.toRealPath(LinkOption.NOFOLLOW_LINKS));
      }
      catch (IOException e) {
        real = true;
      }
      myReal = real;
    }

    @NotNull
    @Override
    public Path getLocalRoot() {
      return myLocalRoot;
    }

    @NotNull
    @Override
    public String getTargetRoot() {
      return myTargetRoot.toString();
    }

    @NotNull
    @Override
    public String resolveTargetPath(@NotNull String relativePath) throws IOException {
      if (myReal) {
        File targetFile = myTargetRoot.resolve(relativePath).toFile().getCanonicalFile();
        return targetFile.toString();
      }
      else {
        // myLocalRoot used there intentionally, because it could contain a relative path that some test expects to get.
        return FileUtil.toCanonicalPath(FileUtil.join(myLocalRoot.toString(), relativePath)).replace('/', File.separatorChar);
      }
    }

    @Override
    public void upload(@NotNull String relativePath,
                       @NotNull TargetProgressIndicator targetProgressIndicator) throws IOException {
      if (myReal) {
        File targetFile = myTargetRoot.resolve(relativePath).toFile().getCanonicalFile();
        FileUtil.copyFileOrDir(myLocalRoot.resolve(relativePath).toFile().getCanonicalFile(), targetFile);
      }
    }

    @Override
    public void download(@NotNull String relativePath, @NotNull ProgressIndicator progressIndicator) throws IOException {
      if (myReal) {
        FileUtil.copyFileOrDir(
          myTargetRoot.resolve(relativePath).toFile().getCanonicalFile(),
          myLocalRoot.resolve(relativePath).toFile().getCanonicalFile());
      }
    }
  }
}


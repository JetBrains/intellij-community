/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author nik
 */
public class JdkVersionDetectorImpl extends JdkVersionDetector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.SdkVersionUtil");
  private static final ActionRunner ACTION_RUNNER = new ActionRunner() {
    @Override
    public Future<?> run(Runnable runnable) {
      return SharedThreadPool.getInstance().executeOnPooledThread(runnable);
    }
  };

  @Override
  @Nullable
  public String detectJdkVersion(@NotNull String homePath) {
    return detectJdkVersion(homePath, ACTION_RUNNER);
  }

  @Nullable
  public String detectJdkVersion(@NotNull String homePath, @NotNull ActionRunner runner) {
    File rtFile = new File(homePath, "jre/lib/rt.jar");
    if (rtFile.isFile()) {
      try {
        JarFile rtJar = new JarFile(rtFile, false);
        try {
          Manifest manifest = rtJar.getManifest();
          if (manifest != null) {
            String version = manifest.getMainAttributes().getValue("Implementation-Version");
            if (version != null) {
              return "java version \"" + version + "\"";
            }
          }
        }
        finally {
          rtJar.close();
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    JdkVersionInfo info = detectJdkVersionInfo(homePath, runner);
    return info != null ? info.getVersion() : null;
  }

  @Override
  public JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath) {
    return detectJdkVersionInfo(homePath, ACTION_RUNNER);
  }

  @Override
  public JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ActionRunner runner) {
    File releaseFile = new File(homePath, "release");
    if (releaseFile.isFile()) {
      try {
        Properties p = new Properties();
        p.load(new FileInputStream(releaseFile));
        String version = p.getProperty("JAVA_FULL_VERSION", p.getProperty("JAVA_VERSION"));
        if (version != null) {
          version = StringUtil.unquoteString(version);
          int i = version.indexOf('+');
          if (i > 0) {
            version = version.substring(0, i);
          }
          String arch = StringUtil.unquoteString(p.getProperty("OS_ARCH", ""));
          boolean x64 = "x86_64".equals(arch) || "amd64".equals(arch);
          return new JdkVersionInfo("java version \"" + version + "\"", x64 ? Bitness.x64 : Bitness.x32);
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    String javaExe = homePath + File.separator + "bin" + File.separator + (SystemInfo.isWindows ? "java.exe" : "java");
    if (new File(javaExe).canExecute()) {
      try {
        Process process = new ProcessBuilder(javaExe, "-version").redirectErrorStream(true).start();
        VersionOutputReader reader = new VersionOutputReader(process.getInputStream(), runner);
        try {
          process.waitFor();
        }
        catch (InterruptedException e) {
          LOG.info(e);
          process.destroy();
        }
        return reader.getVersionInfo();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    return null;
  }

  private static class VersionOutputReader extends BaseOutputReader {
    private static final BaseOutputReader.Options OPTIONS = new BaseOutputReader.Options() {
      @Override public SleepingPolicy policy() { return SleepingPolicy.BLOCKING; }
      @Override public boolean splitToLines() { return true; }
      @Override public boolean sendIncompleteLines() { return false; }
      @Override public boolean withSeparators() { return false; }
    };

    private final ActionRunner myRunner;
    private final List<String> myLines;

    public VersionOutputReader(@NotNull InputStream stream, @NotNull ActionRunner runner) {
      super(stream, CharsetToolkit.getDefaultSystemCharset(), OPTIONS);
      myRunner = runner;
      myLines = new CopyOnWriteArrayList<String>();
      start("java -version");
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return myRunner.run(runnable);
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myLines.add(text);
    }

    @Nullable
    public JdkVersionInfo getVersionInfo() {
      String version = null;
      Bitness arch = Bitness.x32;

      for (String line : myLines) {
        if (line.contains("version")) {
          if (version == null) {
            version = line;
          }
        }
        else if (line.contains("64-Bit") || line.contains("x86_64") || line.contains("amd64")) {
          arch = Bitness.x64;
        }
      }

      return version != null ? new JdkVersionInfo(version, arch) : null;
    }
  }
}
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
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Bitness;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.concurrent.Future;

/**
 * @author nik
 */
public abstract class JdkVersionDetector {
  public static JdkVersionDetector getInstance() {
    return JpsServiceManager.getInstance().getService(JdkVersionDetector.class);
  }

  /**
   * Returns java version for JDK located at {@code homePath} in format like<br>
   * <tt>java version "1.8.0_40"</tt><br>
   * by running '<tt>java -version</tt>' command
   * @param homePath path to JDK home directory
   * @return version string of {@code null} if version cannot be determined
   */
  @Nullable
  public abstract String detectJdkVersion(@NotNull String homePath);

  @Nullable
  public abstract String detectJdkVersion(@NotNull String homePath, @NotNull ActionRunner actionRunner);

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath);

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@NotNull String homePath, @NotNull ActionRunner actionRunner);

  //todo[nik] replace with a service with different implementations for IDE process and for JPS process (need to exclude jps-builders module from IDEA classpath)
  public interface ActionRunner {
    Future<?> run(Runnable runnable);
  }

  public static final class JdkVersionInfo {
    private final String myVersion;
    private final Bitness myBitness;

    public JdkVersionInfo(@NotNull String version, @NotNull Bitness bitness) {
      myVersion = version;
      myBitness = bitness;
    }

    @NotNull
    public String getVersion() {
      return myVersion;
    }

    @NotNull
    public Bitness getBitness() {
      return myBitness;
    }
  }
}
/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class JdkBundleList {
  @NotNull private static final Logger LOG = Logger.getInstance("#com.intellij.util.JdkBundleList");

  private ArrayList<JdkBundle> bundleList = new ArrayList<>();
  private HashMap<String, JdkBundle> bundleMap = new HashMap<>();
  private HashMap<String, JdkBundle> nameVersionMap = new HashMap<>();

  public void addBundle(@NotNull JdkBundle bundle, boolean forceOldVersion) {
    JdkBundle bundleDescr = bundleMap.get(bundle.getAbsoluteLocation().getAbsolutePath());
    if (bundleDescr == null) {
      addMostRecent(bundle, forceOldVersion);
    }
    else {
      if (bundle.isBundled()) bundleDescr.setBundled(true); // preserve bundled flag
      if (bundle.isBoot()) bundleDescr.setBoot(true); // preserve boot flag
    }
  }

  public JdkBundle getBundle(@NotNull String path) {
    return bundleMap.get(path);
  }

  private void addMostRecent(@NotNull JdkBundle bundleDescriptor, boolean forceOldVersion) {
    Pair<Version, Integer> versionUpdate = bundleDescriptor.getVersionUpdate();
    boolean updateVersionMap = versionUpdate != null;
    if (!bundleList.isEmpty() && updateVersionMap) {
      JdkBundle latestJdk = nameVersionMap.get(bundleDescriptor.getNameVersion());
      if (latestJdk != null) {
        Pair<Version, Integer> latestVersionUpdate = latestJdk.getVersionUpdate();
        if (latestVersionUpdate != null) {
          if (latestVersionUpdate.second >= versionUpdate.second) {
            if (!forceOldVersion) return; // do not add old non bundled jdk builds unless asked

            updateVersionMap = false; // include bundled version but do not update map
          }
          else if (!latestJdk.isBoot() && !latestJdk.isBundled()) { // preserve boot and bundled versions
            bundleList.remove(latestJdk);
            nameVersionMap.remove(latestJdk.getNameVersion());
            bundleMap.remove(latestJdk.getAbsoluteLocation().getAbsolutePath());
          }
        }
      }
    }

    bundleList.add(bundleDescriptor);
    bundleMap.put(bundleDescriptor.getAbsoluteLocation().getAbsolutePath(), bundleDescriptor);

    if (updateVersionMap) {
      nameVersionMap.put(bundleDescriptor.getNameVersion(), bundleDescriptor);
    }
  }

  public void addBundlesFromLocation(@NotNull String location, @Nullable Version minVer, @Nullable Version maxVer) {
    File jvmLocation = new File(location);

    if (!jvmLocation.exists()) {
      LOG.debug("Standard jvm location does not exists: " + jvmLocation);
      return;
    }

    File[] jvms = jvmLocation.listFiles();

    if (jvms == null) {
      LOG.debug("Cannot get jvm list from: " + jvmLocation);
      return;
    }

    for (File jvm : jvms) {
      JdkBundle jvmBundle = JdkBundle.createBundle(jvm, false, false);
      if (jvmBundle == null || jvmBundle.getVersionUpdate() == null) continue;

      Version jdkVer = jvmBundle.getVersion();
      if (jdkVer == null) continue; // Skip unknown

      if (minVer != null && jdkVer.lessThan(minVer.major, minVer.minor, minVer.bugfix)) {
        continue; // Skip below supported
      }

      if (maxVer != null && maxVer.lessThan(jdkVer.major, jdkVer.minor, jdkVer.bugfix)) {
        continue; // Skip above supported
      }

      addBundle(jvmBundle, false);
    }
  }

  public ArrayList<JdkBundle> toArrayList() {
    return bundleList;
  }

  public boolean contains(@NotNull String path) {
    return bundleMap.keySet().contains(path);
  }
}

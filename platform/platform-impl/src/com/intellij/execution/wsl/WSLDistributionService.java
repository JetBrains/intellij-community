// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;


import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for keeping list of distributions in the external file, available for user modifications.
 * To force IDE to store default values (as an example for users) we use empty list as default and initializing on
 * first read. Configuration available at: {@code HOME\.IntelliJIdea2018.2\config\options\wsl.distributions.xml}
 */
@State(
  name = "WslDistributionsService",
  storages = @Storage(value = "wsl.distributions.xml")
)
class WSLDistributionService implements PersistentStateComponent<WSLDistributionService> {
  @Tag("descriptors")
  private final List<WslDistributionDescriptor> myDescriptors = new ArrayList<>();

  public List<WslDistributionDescriptor> getDescriptors() {
    if (myDescriptors.isEmpty()) {
      init();
    }
    return myDescriptors;
  }

  private synchronized void init() {
    if (!myDescriptors.isEmpty()) {
      return;
    }
    ContainerUtil.addAll(
      myDescriptors,
      new WslDistributionDescriptor("DEBIAN", "Debian", "debian.exe", "Debian GNU/Linux"),
      new WslDistributionDescriptor("KALI", "kali-linux", "kali.exe", "Kali Linux"),
      new WslDistributionDescriptor("OPENSUSE42", "openSUSE-42", "opensuse-42.exe", "openSUSE Leap 42"),
      new WslDistributionDescriptor("SLES12", "SLES-12", "sles-12.exe", "SUSE Linux Enterprise Server 12"),
      new WslDistributionDescriptor("UBUNTU", "Ubuntu", "ubuntu.exe", "Ubuntu"),
      new WslDistributionDescriptor("UBUNTU1604", "Ubuntu-16.04", "ubuntu1604.exe", "Ubuntu 16.04"),
      new WslDistributionDescriptor("UBUNTU1804", "Ubuntu-18.04", "ubuntu1804.exe", "Ubuntu 18.04")
    );
  }

  @Nullable
  @Override
  public WSLDistributionService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull WSLDistributionService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static WSLDistributionService getInstance() {
    return ServiceManager.getService(WSLDistributionService.class);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;


import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Service responsible for keeping list of distributions in the external file, available for user modifications.
 * @apiNote To force IDE to store default values (as an example for users) we use empty list as default and initializing on
 * first read. Configuration available at: {@code HOME\.IntelliJIdea2018.2\config\options\wsl.distributions.xml}
 * <br/>
 * Service automatically adds default descriptors on first invocation.
 */
@State(
  name = "WslDistributionsService",
  storages = @Storage(value = "wsl.distributions.xml")
)
class WSLDistributionService implements PersistentStateComponent<WSLDistributionService> {
  /**
   * Current service implementation version is necessary for future migrations: fields additions and so on.
   */
  private static final int CURRENT_VERSION = 1;

  /**
   * Persisted service version. Migration should be performed if differs from {@link #CURRENT_VERSION}
   */
  @Attribute("version")
  private int myVersion = 0;

  @Tag("descriptors")
  @NotNull
  private final Set<WslDistributionDescriptor> myDescriptors = new LinkedHashSet<>();

  private static final List<WslDistributionDescriptor> DEFAULT_DESCRIPTORS = Arrays.asList(
    new WslDistributionDescriptor("DEBIAN", "Debian", "debian.exe", "Debian GNU/Linux"),
    new WslDistributionDescriptor("KALI", "kali-linux", "kali.exe", "Kali Linux"),
    new WslDistributionDescriptor("OPENSUSE42", "openSUSE-42", "opensuse-42.exe", "openSUSE Leap 42"),
    new WslDistributionDescriptor("SLES12", "SLES-12", "sles-12.exe", "SUSE Linux Enterprise Server 12"),
    new WslDistributionDescriptor("UBUNTU", "Ubuntu", "ubuntu.exe", "Ubuntu"),
    new WslDistributionDescriptor("UBUNTU1604", "Ubuntu-16.04", "ubuntu1604.exe", "Ubuntu 16.04"),
    new WslDistributionDescriptor("UBUNTU1804", "Ubuntu-18.04", "ubuntu1804.exe", "Ubuntu 18.04")
  );

  /**
   * Atomic applier of default values: distributions and persisted version.
   * This hack is necessary, because there is no way to force our PersistentStateComponent to save default values
   * It can't be put to {@link #loadState(WSLDistributionService)} or {@link #noStateLoaded()} because of serialization implementations
   * details
   */
  private final AtomicNullableLazyValue<Boolean> myDefaultsApplier = AtomicNullableLazyValue.createValue(() -> {
    myDescriptors.addAll(DEFAULT_DESCRIPTORS);
    myVersion = CURRENT_VERSION;
    return true;
  });


  @NotNull
  public Collection<WslDistributionDescriptor> getDescriptors() {
    myDefaultsApplier.getValue();
    return myDescriptors;
  }

  @Nullable
  @Override
  public WSLDistributionService getState() {
    return this;
  }

  /**
   * @implSpec migrations if any, should be done here, depending on {@link #myVersion} of {@code state} and {@link #CURRENT_VERSION}
   */
  @Override
  public void loadState(@NotNull WSLDistributionService state) {
    XmlSerializerUtil.copyBean(state, this);
    myDescriptors.removeIf(it -> !it.isValid());
  }

  @NotNull
  public static WSLDistributionService getInstance() {
    return ServiceManager.getService(WSLDistributionService.class);
  }
}

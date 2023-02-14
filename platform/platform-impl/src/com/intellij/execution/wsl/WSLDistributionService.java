// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

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
  storages = @Storage(value = "wsl.distributions.xml", roamingType = RoamingType.DISABLED)
)
@Service(Service.Level.APP)
final class WSLDistributionService implements PersistentStateComponent<WSLDistributionService> {
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
    new WslDistributionDescriptor("SLES15", "SLES-15", "sles-15.exe", "SUSE Linux Enterprise Server 15"),
    new WslDistributionDescriptor("SLES-15-SP1", "SLES-15-SP1.exe", "SUSE Linux Enterprise Server 15 SP1"),
    new WslDistributionDescriptor("SUSE-Linux-Enterprise-Server-15-SP2", "SUSE-Linux-Enterprise-Server-15-SP2.exe", "SUSE Linux Enterprise Server 15 SP2"),
    new WslDistributionDescriptor("OPENSUSE15", "openSUSE-Leap-15", "openSUSE-Leap-15.exe", "openSUSE Leap 15"),
    new WslDistributionDescriptor("OPENSUSE15-1", "openSUSE-Leap-15-1", "openSUSE-Leap-15-1.exe", "openSUSE Leap 15.1"),
    new WslDistributionDescriptor("OPENSUSE15.2", "openSUSE-Leap-15.2", "openSUSE-Leap-15.2.exe", "openSUSE Leap 15.2"),
    new WslDistributionDescriptor("UBUNTU", "Ubuntu", "ubuntu.exe", "Ubuntu"),
    new WslDistributionDescriptor("UBUNTU1604", "Ubuntu-16.04", "ubuntu1604.exe", "Ubuntu 16.04"),
    new WslDistributionDescriptor("UBUNTU1804", "Ubuntu-18.04", "ubuntu1804.exe", "Ubuntu 18.04"),
    new WslDistributionDescriptor("UBUNTU2004", "Ubuntu-20.04", "ubuntu2004.exe", "Ubuntu 20.04"),
    new WslDistributionDescriptor("Ubuntu-CommPrev", "ubuntupreview.exe", "Ubuntu on Windows Community Preview"),
    // WLinux was renamed to Pengwin, but the distribution name is unchanged (https://github.com/WhitewaterFoundry/Pengwin/blob/f8ae3ab1e207cea0dd08f92aa1b3b79f66013916/DistroLauncher/DistributionInfo.h#L16)
    new WslDistributionDescriptor("PENGWIN", "WLinux", "pengwin.exe", "Pengwin"),
    new WslDistributionDescriptor("PENGWIN_ENTERPRISE", "WLE", "wle.exe", "Pengwin Enterprise"),
    new WslDistributionDescriptor("fedoraremix", "fedoraremix.exe", "Fedora Remix for WSL"),
    new WslDistributionDescriptor("ARCH", "Arch", "Arch.exe", "Arch Linux")
  );

  /**
   * Atomic applier of default values: distributions and persisted version.
   * This hack is necessary, because there is no way to force our PersistentStateComponent to save default values
   * It can't be put to {@link #loadState(WSLDistributionService)} or {@link #noStateLoaded()} because of serialization implementations
   * details
   */
  private final NullableLazyValue<Boolean> myDefaultsApplier = NullableLazyValue.atomicLazyNullable(() -> {
    myDescriptors.addAll(DEFAULT_DESCRIPTORS);
    myVersion = CURRENT_VERSION;
    return true;
  });


  @NotNull
  public Collection<WslDistributionDescriptor> getDescriptors() {
    myDefaultsApplier.getValue();
    return myDescriptors;
  }

  @Override
  public @NotNull WSLDistributionService getState() {
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
    return ApplicationManager.getApplication().getService(WSLDistributionService.class);
  }
}

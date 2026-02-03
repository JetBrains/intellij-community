// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VagrantCredentialsHandler extends RemoteCredentialsHandlerBase<VagrantBasedCredentialsHolder> {

  public static final @NonNls String VAGRANT_PREFIX = "vagrant://";

  public VagrantCredentialsHandler(VagrantBasedCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public @NotNull String getId() {
    VagrantBasedCredentialsHolder cred = getCredentials();
    return VAGRANT_PREFIX + cred.getVagrantFolder()
           + (StringUtil.isNotEmpty(cred.getMachineName()) ?
              "@" + cred.getMachineName() : "");
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    VagrantBasedCredentialsHolder cred = getCredentials();
    String pathRelativeToHome = FileUtil.getLocationRelativeToUserHome(cred.getVagrantFolder());
    return IdeBundle.message("vagrant.vm.1.choice.0.0.1.at.2.3", cred.getMachineName(), StringUtil.isNotEmpty(cred.getMachineName()) ? 0 : 1,
               pathRelativeToHome.length() < cred.getVagrantFolder().length() ? pathRelativeToHome : cred.getVagrantFolder(),
               interpreterPath);
  }

  @Override
  public void load(@Nullable Element rootElement) {
    if (rootElement != null) {
      getCredentials().load(rootElement);
    }
  }
}

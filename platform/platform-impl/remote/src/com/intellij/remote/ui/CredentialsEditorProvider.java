// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui;

import com.intellij.openapi.project.Project;
import com.intellij.remote.ext.CredentialsEditor;
import com.intellij.remote.ext.CredentialsLanguageContribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CredentialsEditorProvider {

  boolean isAvailable(CredentialsLanguageContribution languageContribution);

  CredentialsEditor createEditor(@Nullable Project project,
                                 CredentialsLanguageContribution languageContribution,
                                 @NotNull RemoteSdkEditorForm parentForm);

  String getDefaultInterpreterPath(BundleAccessor bundleAccessor);
}

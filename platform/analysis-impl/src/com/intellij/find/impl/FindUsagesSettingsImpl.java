// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindUsagesSettings;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.ApiStatus;

@State(name = "FindUsagesSettings", storages = @Storage("findUsages.xml"))
@ApiStatus.Internal
public class FindUsagesSettingsImpl extends FindSettingsBase implements FindUsagesSettings {
}

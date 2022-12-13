// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface InspectionProfileLoader {
  @Nullable
  InspectionProfileImpl loadProfileByName(@NotNull String profileName);

  @Nullable
  InspectionProfileImpl loadProfileByPath(@NotNull String profilePath) throws IOException, JDOMException;
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class OsNameMacro extends Macro {
   @Override
   public @NotNull String getName() {
     return "OSName";
   }

   @Override
   public @NotNull String getDescription() {
     return IdeCoreBundle.message("macro.os.name");
   }

   @Override
   public @NotNull String expand(@NotNull DataContext dataContext) {
     String osName = SystemInfoRt.OS_NAME.toLowerCase(Locale.ENGLISH);
     int firstSpace = osName.indexOf(' ');
     return firstSpace < 0 ? osName : osName.substring(0, firstSpace);
   }
 }
 // Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 package com.intellij.ide.macro;

 import com.intellij.ide.IdeCoreBundle;
 import com.intellij.openapi.actionSystem.DataContext;
 import com.intellij.openapi.util.SystemInfo;
 import com.intellij.util.SystemProperties;
 import org.jetbrains.annotations.NotNull;
 import org.jetbrains.annotations.Nullable;

 public class TempDirMacro extends Macro {
   @NotNull
   @Override
   public String getName() {
     return "TempDir";
   }

   @NotNull
   @Override
   public String getDescription() {
     return IdeCoreBundle.message("macro.temp.dir");
   }

   @Nullable
   @Override
   public String expand(@NotNull DataContext dataContext) {
     if (SystemInfo.isWindows) {
       String tempDir = System.getenv("TEMP");
       if (tempDir == null) {
         String homeDir = SystemProperties.getUserHome();
         tempDir = homeDir + "\\AppData\\Local\\Temp";
       }
       return tempDir;
     }

     return "/tmp";
   }
 }
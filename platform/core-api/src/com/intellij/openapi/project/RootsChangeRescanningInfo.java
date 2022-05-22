// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

public interface RootsChangeRescanningInfo {

  RootsChangeRescanningInfo TOTAL_RESCAN = new RootsChangeRescanningInfo() {
    @Override
    public String toString() {
      return "RootsChangeRescanningInfo.TOTAL_RESCAN";
    }
  };

  RootsChangeRescanningInfo NO_RESCAN_NEEDED = new RootsChangeRescanningInfo() {
    @Override
    public String toString() {
      return "RootsChangeRescanningInfo.NO_RESCAN_NEEDED";
    }
  };
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsFileTypesConfiguration;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

final class JpsFileTypesConfigurationImpl extends JpsElementBase<JpsFileTypesConfigurationImpl> implements JpsFileTypesConfiguration {
  public static final JpsElementChildRole<JpsFileTypesConfiguration> ROLE = JpsElementChildRoleBase.create("file types");
  private String myIgnoredPatternString;

  JpsFileTypesConfigurationImpl() {
    this("CVS;.DS_Store;.svn;.pyc;.pyo;*.pyc;*.pyo;.git;*.hprof;_svn;.hg;*.lib;*~;__pycache__;.bundle;vssver.scc;vssver2.scc;*.rbc;");
  }

  private JpsFileTypesConfigurationImpl(String ignoredPatternString) {
    myIgnoredPatternString = ignoredPatternString;
  }

  @Override
  public @NotNull JpsFileTypesConfigurationImpl createCopy() {
    return new JpsFileTypesConfigurationImpl(myIgnoredPatternString);
  }

  @Override
  public String getIgnoredPatternString() {
    return myIgnoredPatternString;
  }

  @Override
  public void setIgnoredPatternString(@NotNull String ignoredPatternString) {
    if (!myIgnoredPatternString.equals(ignoredPatternString)) {
      myIgnoredPatternString = ignoredPatternString;
    }
  }
}

package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsFileTypesConfiguration;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author nik
 */
public class JpsFileTypesConfigurationImpl extends JpsElementBase<JpsFileTypesConfigurationImpl> implements JpsFileTypesConfiguration {
  public static final JpsElementChildRole<JpsFileTypesConfiguration> ROLE = JpsElementChildRoleBase.create("file types");
  private String myIgnoredPatternString;

  public JpsFileTypesConfigurationImpl() {
    this("CVS;SCCS;RCS;rcs;.DS_Store;.svn;.pyc;.pyo;*.pyc;*.pyo;.git;*.hprof;_svn;.hg;*.lib;*~;__pycache__;.bundle;vssver.scc;vssver2.scc;*.rbc;");
  }

  private JpsFileTypesConfigurationImpl(String ignoredPatternString) {
    myIgnoredPatternString = ignoredPatternString;
  }

  @NotNull
  @Override
  public JpsFileTypesConfigurationImpl createCopy() {
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
      fireElementChanged();
    }
  }

  @Override
  public void applyChanges(@NotNull JpsFileTypesConfigurationImpl modified) {
    setIgnoredPatternString(modified.myIgnoredPatternString);
  }
}

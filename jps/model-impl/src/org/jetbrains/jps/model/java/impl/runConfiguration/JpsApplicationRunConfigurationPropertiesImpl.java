package org.jetbrains.jps.model.java.impl.runConfiguration;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationState;

/**
 * @author nik
 */
public class JpsApplicationRunConfigurationPropertiesImpl extends JpsElementBase<JpsApplicationRunConfigurationPropertiesImpl> implements JpsApplicationRunConfigurationProperties {
  private JpsApplicationRunConfigurationState myState;

  public JpsApplicationRunConfigurationPropertiesImpl(JpsApplicationRunConfigurationState state) {
    myState = state;
  }

  @NotNull
  @Override
  public JpsApplicationRunConfigurationPropertiesImpl createCopy() {
    return new JpsApplicationRunConfigurationPropertiesImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull JpsApplicationRunConfigurationPropertiesImpl modified) {
    XmlSerializerUtil.copyBean(modified.myState, myState);
  }

  @Override
  public String getMainClass() {
    return myState.MAIN_CLASS_NAME;
  }

  @Override
  public void setMainClass(String value) {
    myState.MAIN_CLASS_NAME = value;
  }
}

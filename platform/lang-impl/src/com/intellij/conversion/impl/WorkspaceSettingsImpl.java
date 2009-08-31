package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.WorkspaceSettings;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class WorkspaceSettingsImpl extends ComponentManagerSettingsImpl implements WorkspaceSettings {
  @NonNls public static final String RUN_MANAGER_COMPONENT_NAME = "RunManager";
  @NonNls public static final String CONFIGURATION_ELEMENT = "configuration";

  public WorkspaceSettingsImpl(File workspaceFile) throws CannotConvertException {
    super(workspaceFile);
  }

  @NotNull
  public Collection<? extends Element> getRunConfigurations() {
    final Element element = getComponentElement(RUN_MANAGER_COMPONENT_NAME);
    if (element == null) {
      return Collections.emptyList();
    }

    return JDomConvertingUtil.getChildren(element, CONFIGURATION_ELEMENT);
  }

}

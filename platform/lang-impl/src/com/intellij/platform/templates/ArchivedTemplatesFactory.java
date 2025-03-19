// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.impl.UrlUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class ArchivedTemplatesFactory extends ProjectTemplatesFactory {
  private static final Logger LOG = Logger.getInstance(ArchivedTemplatesFactory.class);

  static final String ZIP = ".zip";

  private static @NotNull URL getCustomTemplatesURL() {
    try {
      return new File(getCustomTemplatesPath()).toURI().toURL();
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  static @NotNull String getCustomTemplatesPath() {
    return PathManager.getConfigPath() + "/projectTemplates"; //NON-NLS
  }

  public static @NotNull Path getTemplateFile(String name) {
    return Paths.get(getCustomTemplatesPath(), name + ".zip");
  }

  @Override
  public String @NotNull [] getGroups() {
    return new String[]{CUSTOM_GROUP};
  }

  @Override
  public ProjectTemplate @NotNull [] createTemplates(@Nullable String group, @NotNull WizardContext context) {
    return createTemplates(group);
  }

  public ProjectTemplate @NotNull [] createTemplates(@Nullable String group) {
    // myGroups contains only not-null keys
    if (!CUSTOM_GROUP.equals(group)) {
      return ProjectTemplate.EMPTY_ARRAY;
    }

    List<ProjectTemplate> templates = null;
    URL url = getCustomTemplatesURL();
    try {
      for (String child : UrlUtil.getChildrenRelativePaths(url)) {
        if (child.endsWith(ZIP)) {
          if (templates == null) {
            templates = new SmartList<>();
          }
          templates.add(new LocalArchivedTemplate(new URL(url.toExternalForm() + '/' + child), ClassLoader.getSystemClassLoader()));
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return ContainerUtil.isEmpty(templates) ? ProjectTemplate.EMPTY_ARRAY : templates.toArray(ProjectTemplate.EMPTY_ARRAY);
  }

  @Override
  public int getGroupWeight(String group) {
    return CUSTOM_GROUP.equals(group) ? -2 : 0;
  }

  @Override
  public Icon getGroupIcon(String group) {
    return CUSTOM_GROUP.equals(group) ? AllIcons.General.User : super.getGroupIcon(group);
  }
}

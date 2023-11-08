// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.jarRepository;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoriesConfiguration;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class JpsRemoteRepositoriesConfigurationSerializer extends JpsProjectExtensionSerializer {
  private static final String ELEMENT_TAG = "remote-repository";
  private static final String OPTION_TAG = "option";
  private static final String ID_PROPERTY = "id";
  private static final String NAME_PROPERTY = "name";
  private static final String URL_PROPERTY = "url";

  public JpsRemoteRepositoriesConfigurationSerializer() {
    super("jarRepositories.xml", "RemoteRepositoriesConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    List<JpsRemoteRepositoryDescription> result = new ArrayList<>();
    List<Element> children = componentTag.getChildren(ELEMENT_TAG);
    for (Element repoElement : children) {
      String id = null;
      String name = null;
      String url = null;
      for (Element element : repoElement.getChildren(OPTION_TAG)) {
        String option = element.getAttributeValue("name");
        String optionValue = element.getAttributeValue("value");
        if (ID_PROPERTY.equals(option)) {
          id = optionValue;
        }
        else if (NAME_PROPERTY.equals(option)) {
          name = optionValue == null? "" : optionValue;
        }
        else if (URL_PROPERTY.equals(option)) {
          url = optionValue;
        }
      }
      if (id != null && url != null) {
        result.add(new JpsRemoteRepositoryDescription(id, name, url));
      }
    }
    JpsRemoteRepositoriesConfiguration config = JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(project);
    if (!result.isEmpty()) {
      config.setRepositories(result);
    }
  }
}

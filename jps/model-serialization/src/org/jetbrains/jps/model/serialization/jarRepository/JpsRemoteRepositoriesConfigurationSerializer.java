/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization.jarRepository;

import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoriesConfiguration;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JpsRemoteRepositoriesConfigurationSerializer extends JpsProjectExtensionSerializer {
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
    final List<JpsRemoteRepositoryDescription> result = new SmartList<>();
    final List<Element> children = componentTag.getChildren(ELEMENT_TAG);
    for (Element repoElement : children) {
      String id = null;
      String name = null;
      String url = null;
      for (Element element : repoElement.getChildren(OPTION_TAG)) {
        final String option = element.getAttributeValue("name");
        final String optionValue = element.getAttributeValue("value");
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
    final JpsRemoteRepositoriesConfiguration config = JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(project);
    if (!result.isEmpty()) {
      config.setRepositories(result);
    }
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
     // not supported 
  }
}

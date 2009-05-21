package com.intellij.ui.tabs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.ui.FileColorManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
@State(
  name="SharedFileColors",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$"),
    @Storage(id = "other", file = "$PROJECT_CONFIG_DIR$/fileColors.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class FileColorSharedConfigurationManager implements PersistentStateComponent<Element> {
  private Project myProject;

  public FileColorSharedConfigurationManager(@NotNull final Project project) {
    myProject = project;
  }

  public Element getState() {
    return ((FileColorManagerImpl)FileColorManager.getInstance(myProject)).getState(true);
  }

  public void loadState(Element state) {
    ((FileColorManagerImpl)FileColorManager.getInstance(myProject)).loadState(state, true);
  }
}

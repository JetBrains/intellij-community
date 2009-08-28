package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;


@State(
  name = "CodeStyleSettingsManager",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/projectCodeStyle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ProjectCodeStyleSettingsManager extends CodeStyleSettingsManager{
}

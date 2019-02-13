// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class ExternalPrebuiltIndexManager {
  private final Project myProject;

  private volatile String myIndexPath;
  private volatile boolean myEnabled;

  public ExternalPrebuiltIndexManager(@NotNull Project project) {
    myIndexPath = PropertiesComponent.getInstance(project).getValue("external.prebuilt.indices.path");
    myEnabled = Registry.is("use.external.prebuilt.indices") && PropertiesComponent.getInstance(project).getBoolean("external.prebuilt.indices.for.project", true);
    myProject = project;

    if (myIndexPath == null) {
      if (myEnabled) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
          @Override
          public void enteredDumbMode() {
            connection.disconnect();
            showDialog();
            if (myIndexPath != null) {
              openIndices(myIndexPath);
            }
          }
        });
      }
    } else {
      openIndices(myIndexPath);
    }
  }

  private void openIndices(@NotNull String baseDir) {


  }

  private void showDialog() {
    if (Messages.showYesNoDialog(myProject, "Would you like specify prebuilt indices directory", "Prebuilt Indices", null) == Messages.YES) {
      PropertiesComponent.getInstance(myProject).setValue("external.prebuilt.indices.for.project", true);
      VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), myProject, null);
      if (file != null) {
        String path = file.getPath();
        PropertiesComponent.getInstance(myProject).setValue("external.prebuilt.indices.path", path);
        myIndexPath = path;
      }
    }
    myEnabled = false;
    PropertiesComponent.getInstance(myProject).setValue("external.prebuilt.indices.for.project", false);
  }
}

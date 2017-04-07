/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.tools.javac.util.List;

import javax.swing.*;

import static com.intellij.openapi.ui.Messages.*;

/**
 * Created by Miles on 1/26/2016.
 */

public class DimensionViewer extends AnAction {
  public DimensionViewer() {
    super("Dimension _Viewer");
  }

  public void actionPerformed(AnActionEvent event) {
    Project project = event.getData(PlatformDataKeys.PROJECT);
    Icon ii = getInformationIcon();

    //Grab the file and get its list of variations
    //TODO: this will have to account for if a partial selection has already been made on the file
    VirtualFile f = event.getData(DataKeys.VIRTUAL_FILE);
    String fname = f.getName();

    FileVariation fv = DimensionFilter.getFileVariation(fname);
    String mes = "The following choice have been made in this file:\n\tDim\tChoice\n";
    for (Choice c : fv.choices) {
      mes += "\t" + c.dim + "\t" + c.val;
    }
    showMessageDialog(project, mes, "Current Filters", ii);

  }

  //project all changes to the passed file back to the original, then revert selections and display to the user
  //TODO: implement 'clearSelections'
  public void clearSelections(String filename)  {
    return;
  }
}
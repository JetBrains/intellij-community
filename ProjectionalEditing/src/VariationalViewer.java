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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile
import com.sun.tools.javac.util.List;

import javax.swing.*;

import static com.intellij.openapi.ui.Messages.*;

/**
 * Created by Miles on 1/18/2016.
 */

class Choice {
  String dim;
  String val;

  public Choice(String d, String v) {
    dim = d;
    val = v;
  }
}

//a wrapper object around a file that is currently being edited using the variational interface
class FileVariation {
  String originalFile;
  String currentFile; //TODO: these should not be strings, but more likely some more complex data structure
  Choice choice;

  public FileVariation(String of, String cf, Choice c) {
    originalFile = of;
    currentFile = cf;
    choice = c;
  }

}

public class VariationalViewer extends AnAction {
  public VariationalViewer() {
    super("Variational _Viewer");
  }
  public List<FileVariation> variedFiles;

  public void actionPerformed(AnActionEvent event) {
    Project project = event.getData(PlatformDataKeys.PROJECT);
    Icon i = getQuestionIcon();

    //Prompt the user for their choice of dimension filters (only one-at-a-time atm)
    String dimension = showInputDialog(project, "What dimesion would you like to filter by?", "Dimension name",i);
    String value = showInputDialog(project, "What choice would you like to make in dimension " + dimension + "?", "Chocie", i);

    //Grab the file and store it's current state before making any modifications
    //TODO: this will have to account for if a partial selection has already been made on the file
    VirtualFile f = event.getData(DataKeys.VIRTUAL_FILE);
    String name = f.getName();


    //Perform the modification to the file, store it's new state, and display to the user
    Choice c = new Choice(dimension, value);

    //TODO: rather than passing in names, pass in some representation of the original file and the modified file
    FileVariation fv = new FileVariation(name, name, c);
    variedFiles.add(fv);
  }

  //project all changes to the passed file back to the original, then revert selections and display to the user
  //TODO: implement 'clearSelections'
  public void clearSelections(String filename)  {
    return;
  }
}

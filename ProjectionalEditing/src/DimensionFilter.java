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
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.tools.javac.util.List;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.swing.*;

import java.util.ArrayList;

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

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
      // if deriving: appendSuper(super.hashCode()).
        append(dim).
        append(val).
        toHashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Choice))
      return false;
    if (obj == this)
      return true;

    Choice rhs = (Choice) obj;
    return new EqualsBuilder().
      // if deriving: appendSuper(super.equals(obj)).
        append(dim, rhs.dim).
        append(val, rhs.val).
        isEquals();
  }
}

//a wrapper object around a file that is currently being edited using the variational interface
class FileVariation {
  String originalFile; //TODO: these should not be strings, but more likely some more complex data structure
  ArrayList<Choice> choices;

  public FileVariation(String of, Choice c) {
    originalFile = of;
    choices = new ArrayList<Choice>();
    choices.add(c);
  }

  public FileVariation(String of) {
    choices = new ArrayList<Choice>();
    originalFile = of;
  }

  //@return true if adding successful
  //        false if choice had already been made
  public boolean makeChoice(Choice c) {
    if (this.choices.contains(c)) return false;
    else this.choices.add(c);
    return true;
  }

}

public class DimensionFilter extends AnAction {
  public DimensionFilter() {
    super("Dimension _Filter");
  }

  private static ArrayList<FileVariation> variedFiles = new ArrayList<FileVariation>();

  public static FileVariation getFileVariation(String fname) {
    for (FileVariation fv : variedFiles) {
      if (fv.originalFile.equals(fname)) return fv;
    }
    //if one doesn't already exist for this file, return a new one
    return new FileVariation(fname);
  }

  //TODO: return a more descriptive enum, rather than just a boolean
  public static boolean varyFile(String fname, Choice c) {
    for (FileVariation fv : variedFiles) {
      if (fv.originalFile.equals(fname)) return fv.makeChoice(c);
    }
    //if no appropriate file variation was found, then create a new one
    FileVariation newFV = new FileVariation(fname, c);
    variedFiles.add(newFV);
    return true;
  }


  public void actionPerformed(AnActionEvent event) {
    Project project = event.getData(PlatformDataKeys.PROJECT);
    Icon qi = getQuestionIcon();
    Icon ii = getInformationIcon();
    Icon ei = getErrorIcon();

    //Prompt the user for their choice of dimension filters (only one-at-a-time atm)
    String dimension = showInputDialog(project, "What dimesion would you like to filter by?", "Dimension name",qi);
    String value = showInputDialog(project, "What choice would you like to make in dimension " + dimension + "?", "Choice", qi);

    //Grab the file and store it's current state before making any modifications
    VirtualFile f = event.getData(DataKeys.VIRTUAL_FILE);
    String fname = f.getName();


    //Perform the modification to the file, store it's new state, and display to the user
    Choice c = new Choice(dimension, value);
    boolean ret = varyFile(fname, c);

    //ret == false indicates a failed save
    if (ret == false) {
      showMessageDialog(project, "This choice cannot be made at this time. Perhaps this choice has already been made?", "Failed", ei);
    } else {
      showMessageDialog(project, "Choice: " + c.val + " has been made in Dimension: " + c.dim + " for File: " + fname, "Success", ii);
    }

  }

  //project all changes to the passed file back to the original, then revert selections and display to the user
  //TODO: implement 'clearSelections'
  public void clearSelections(String filename)  {
    return;
  }
}
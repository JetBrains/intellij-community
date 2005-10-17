/**
 * created at Jan 22, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.*;

public class CompilerTestData implements JDOMExternalizable {
  private Set<String> myPathsToDelete = new HashSet<String>();
  private String[] myDeletedByMake;
  private String[] myToRecompile;

  public void readExternal(Element element) throws InvalidDataException {

    // read paths to be deleted
    myPathsToDelete.clear();
    for (Iterator it = element.getChildren("delete").iterator(); it.hasNext();) {
      Element elem = (Element)it.next();
      for (Iterator pathIt = elem.getChildren().iterator(); pathIt.hasNext();) {
        Element pathElement = (Element)pathIt.next();
        myPathsToDelete.add(pathElement.getAttributeValue("path"));
      }
    }

    // read paths that are expected to be deleted
    List<String> data = new ArrayList<String>();
    for (Iterator it = element.getChildren("deleted_by_make").iterator(); it.hasNext();) {
      Element elem = (Element)it.next();
      for (Iterator pathIt = elem.getChildren().iterator(); pathIt.hasNext();) {
        Element pathElement = (Element)pathIt.next();
        data.add(pathElement.getAttributeValue("path"));
      }
    }
    myDeletedByMake = data.toArray(new String[data.size()]);

    // read paths that are expected to be found by dependencies
    data.clear();
    for (Iterator it = element.getChildren("recompile").iterator(); it.hasNext();) {
      Element elem = (Element)it.next();
      for (Iterator pathIt = elem.getChildren().iterator(); pathIt.hasNext();) {
        Element pathElement = (Element)pathIt.next();
        data.add(pathElement.getAttributeValue("path"));
      }
    }
    myToRecompile = data.toArray(new String[data.size()]);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException("Save not supported");
  }

  public String[] getDeletedByMake() {
    return myDeletedByMake;
  }

  public boolean shouldDeletePath(String path) {
    return myPathsToDelete.contains(path);
  }

  public String[] getToRecompile() {
    return myToRecompile;
  }
}

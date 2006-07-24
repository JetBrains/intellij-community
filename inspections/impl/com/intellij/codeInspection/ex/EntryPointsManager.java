/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 27, 2002
 * Time: 2:57:13 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.ConstructorPicker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.*;

public class EntryPointsManager implements JDOMExternalizable, ProjectComponent{
  private final Map myFQNameToSmartEntryPointRef;

  public EntryPointsManager() {
    myFQNameToSmartEntryPointRef = new LinkedHashMap(); // To keep the order between readExternal to writeExternal
  }

  public static EntryPointsManager getInstance(Project project) {
    return project.getComponent(EntryPointsManager.class);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) throws InvalidDataException {
    Element entryPointsElement = element.getChild("entry_points");
    List content = entryPointsElement.getChildren();
    for (Iterator iterator = content.iterator(); iterator.hasNext();) {
      Element entryElement = (Element) iterator.next();
      if ("entry_point".equals(entryElement.getName())) {
        SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(entryElement);
        myFQNameToSmartEntryPointRef.put(entryPoint.getFQName(), entryPoint);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    Element parentNode = element;
    Element entryPointsElement = new Element("entry_points");

    for (Iterator iterator = myFQNameToSmartEntryPointRef.values().iterator(); iterator.hasNext();) {
      SmartRefElementPointer entryPoint = (SmartRefElementPointer) iterator.next();
      if (entryPoint.isPersistent()) {
        entryPoint.writeExternal(entryPointsElement);
      }
    }

    parentNode.addContent(entryPointsElement);
  }

  public void resolveEntryPoints(final RefManager manager) {
    validateEntryPoints();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (Iterator iterator = myFQNameToSmartEntryPointRef.values().iterator(); iterator.hasNext();) {
          SmartRefElementPointer entryPoint = (SmartRefElementPointer) iterator.next();
          if (entryPoint.resolve(manager)) {
            RefElement refElement = entryPoint.getRefElement();
            ((RefElementImpl)refElement).setEntry(true);
            ((RefElementImpl)refElement).setPermanentEntry(entryPoint.isPersistent());
          }
        }
      }
    });
  }

  private void purgeTemporaryEntryPoints() {
    Collection collection = myFQNameToSmartEntryPointRef.values();
    SmartRefElementPointerImpl[] entries = (SmartRefElementPointerImpl[]) collection.toArray(new SmartRefElementPointerImpl[collection.size()]);
    for (int i = 0; i < entries.length; i++) {
      SmartRefElementPointer entry = entries[i];
      if (!entry.isPersistent()) {
        myFQNameToSmartEntryPointRef.remove(entry.getFQName());
        RefElement refElement = entry.getRefElement();
        if (refElement != null) ((RefElementImpl)refElement).setEntry(false);
        entry.freeReference();
      }
    }
  }

  public void addEntryPoint(RefElement newEntryPoint, boolean isPersistent) {
    if (!newEntryPoint.isValid()) return;
    if (newEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass) newEntryPoint;

      if (refClass.isAnonymous()) {
        // Anonymous class cannot be an entry point.
        return;
      }

      ArrayList<RefMethod> refConstructors = refClass.getConstructors();
      if (refConstructors.size() == 1) {
        addEntryPoint(refConstructors.get(0), isPersistent);
        return;
      } else if (refConstructors.size() > 1) {
        // Many constructors here. Need to ask user which ones are used
        ArrayList pickedConstructors = ConstructorPicker.pickConstructorsFrom(refClass, refConstructors);
        for (int i = 0; i < pickedConstructors.size(); i++) {
          addEntryPoint((RefMethod) pickedConstructors.get(i), isPersistent);
        }

        return;
      }
    }

    if (myFQNameToSmartEntryPointRef.get(newEntryPoint.getExternalName()) == null) {
      SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, isPersistent);
      myFQNameToSmartEntryPointRef.put(entry.getFQName(), entry);
      ((RefElementImpl)newEntryPoint).setEntry(true);
      ((RefElementImpl)newEntryPoint).setPermanentEntry(entry.isPersistent());
    }
  }

  public void removeEntryPoint(RefElement anEntryPoint) {
    if (anEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass) anEntryPoint;
      if (!refClass.isInterface()) {
        anEntryPoint = refClass.getDefaultConstructor();
      }
    }

    if (anEntryPoint == null) return;

    Set set = myFQNameToSmartEntryPointRef.entrySet();
    Object key = null;
    for(Iterator iterator = set.iterator(); iterator.hasNext(); ) {
      Map.Entry entry = (Map.Entry)iterator.next();
      SmartRefElementPointer value = (SmartRefElementPointer) entry.getValue();
      if (value.getRefElement() == anEntryPoint) {
        key = entry.getKey();
        break;
      }
    }

    if (key != null) {
      myFQNameToSmartEntryPointRef.remove(key);
      ((RefElementImpl)anEntryPoint).setEntry(false);
    }
  }

  public SmartRefElementPointer[] getEntryPoints() {
    validateEntryPoints();
    Collection collection = myFQNameToSmartEntryPointRef.values();
    return (SmartRefElementPointer[]) collection.toArray(new SmartRefElementPointerImpl[collection.size()]);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    cleanup();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "EntryPointsManager";
  }

  private void validateEntryPoints() {
    Collection collection = myFQNameToSmartEntryPointRef.values();
    SmartRefElementPointerImpl[] entries = (SmartRefElementPointerImpl[]) collection.toArray(new SmartRefElementPointerImpl[collection.size()]);
    for (int i = 0; i < entries.length; i++) {
      SmartRefElementPointer entry = entries[i];
      RefElement refElement = entry.getRefElement();
      if (refElement != null && !refElement.isValid()) {
        myFQNameToSmartEntryPointRef.remove(entry.getFQName());
      }
    }
  }

  public void cleanup() {
    purgeTemporaryEntryPoints();
    Collection entries = myFQNameToSmartEntryPointRef.values();
    for(Iterator iterator = entries.iterator(); iterator.hasNext(); ) {
      SmartRefElementPointer entry = (SmartRefElementPointer)iterator.next();
      entry.freeReference();
    }
  }
}

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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EntryPointsManagerImpl implements JDOMExternalizable, ProjectComponent, EntryPointsManager {
  private final Map<String, SmartRefElementPointer> myFQNameToSmartEntryPointRef;
  private static final String VERSION = "2.0";
  @NonNls private static final String VERSION_ATTR = "version";
  @NonNls private static final String ENTRY_POINT_ATTR = "entry_point";
  private boolean myAddNonJavaEntries = true;
  private boolean myResolved = false;

  public EntryPointsManagerImpl() {
    myFQNameToSmartEntryPointRef =
        new LinkedHashMap<String, SmartRefElementPointer>(); // To keep the order between readExternal to writeExternal
  }

  public static EntryPointsManagerImpl getInstance(Project project) {
    return project.getComponent(EntryPointsManagerImpl.class);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) throws InvalidDataException {
    Element entryPointsElement = element.getChild("entry_points");
    final String version = entryPointsElement.getAttributeValue(VERSION_ATTR);
    if (!Comparing.strEqual(version, VERSION)) {
      convert(entryPointsElement);
    }
    else {
      List content = entryPointsElement.getChildren();
      for (final Object aContent : content) {
        Element entryElement = (Element)aContent;
        if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
          SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(entryElement);
          myFQNameToSmartEntryPointRef.put(entryPoint.getFQName(), entryPoint);
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    Element entryPointsElement = new Element("entry_points");
    entryPointsElement.setAttribute(VERSION_ATTR, VERSION);
    for (SmartRefElementPointer entryPoint : myFQNameToSmartEntryPointRef.values()) {
      if (entryPoint.isPersistent()) {
        entryPoint.writeExternal(entryPointsElement);
      }
    }

    element.addContent(entryPointsElement);
  }

  public void resolveEntryPoints(final RefManager manager) {
    if (!myResolved) {
      myResolved = true;
      validateEntryPoints();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (SmartRefElementPointer entryPoint : myFQNameToSmartEntryPointRef.values()) {
            if (entryPoint.resolve(manager)) {
              RefEntity refElement = entryPoint.getRefElement();
              ((RefElementImpl)refElement).setEntry(true);
              ((RefElementImpl)refElement).setPermanentEntry(entryPoint.isPersistent());
            }
          }
        }
      });
    }
  }

  private void purgeTemporaryEntryPoints() {
    Collection<SmartRefElementPointer> collection = myFQNameToSmartEntryPointRef.values();
    SmartRefElementPointer[] entries = collection.toArray(new SmartRefElementPointer[collection.size()]);
    for (SmartRefElementPointer entry : entries) {
      if (!entry.isPersistent()) {
        myFQNameToSmartEntryPointRef.remove(entry.getFQName());
        RefElement refElement = (RefElement)entry.getRefElement();
        if (refElement != null) ((RefElementImpl)refElement).setEntry(false);
        entry.freeReference();
      }
    }
  }

  public void addEntryPoint(RefElement newEntryPoint, boolean isPersistent) {
    if (!newEntryPoint.isValid()) return;
    if (newEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass)newEntryPoint;

      if (refClass.isAnonymous()) {
        // Anonymous class cannot be an entry point.
        return;
      }

      ArrayList<RefMethod> refConstructors = refClass.getConstructors();
      if (refConstructors.size() == 1) {
        addEntryPoint(refConstructors.get(0), isPersistent);
        return;
      }
      else if (refConstructors.size() > 1) {
        // Many constructors here. Need to ask user which ones are used
        for (int i = 0; i < refConstructors.size(); i++) {
          addEntryPoint((RefMethod)((ArrayList)refConstructors).get(i), isPersistent);
        }

        return;
      }
    }

    if (myFQNameToSmartEntryPointRef.get(newEntryPoint.getExternalName()) == null) {
      final SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, isPersistent);
      myFQNameToSmartEntryPointRef.put(entry.getFQName(), entry);
      ((RefElementImpl)newEntryPoint).setEntry(true);
      ((RefElementImpl)newEntryPoint).setPermanentEntry(entry.isPersistent());
      if (entry.isPersistent()) { //do save entry points
        final EntryPointsManagerImpl entryPointsManager = EntryPointsManagerImpl.getInstance(newEntryPoint.getElement().getProject());
        if (this != entryPointsManager) {
          entryPointsManager.addEntryPoint(newEntryPoint, true);
        }
      }
    }
  }

  public void removeEntryPoint(RefElement anEntryPoint) {
    if (anEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass)anEntryPoint;
      if (!refClass.isInterface()) {
        anEntryPoint = refClass.getDefaultConstructor();
      }
    }

    if (anEntryPoint == null) return;

    Set<Map.Entry<String, SmartRefElementPointer>> set = myFQNameToSmartEntryPointRef.entrySet();
    String key = null;
    for (Map.Entry<String, SmartRefElementPointer> entry : set) {
      SmartRefElementPointer value = entry.getValue();
      if (value.getRefElement() == anEntryPoint) {
        key = entry.getKey();
        break;
      }
    }

    if (key != null) {
      myFQNameToSmartEntryPointRef.remove(key);
      ((RefElementImpl)anEntryPoint).setEntry(false);
    }

    if (anEntryPoint.isPermanentEntry() && anEntryPoint.isValid()) {
      final Project project = anEntryPoint.getElement().getProject();
      final EntryPointsManagerImpl entryPointsManager = EntryPointsManagerImpl.getInstance(project);
      if (this != entryPointsManager) {
        entryPointsManager.removeEntryPoint(anEntryPoint);
      }
    }
  }

  public SmartRefElementPointer[] getEntryPoints() {
    validateEntryPoints();
    Collection<SmartRefElementPointer> collection = myFQNameToSmartEntryPointRef.values();
    return collection.toArray(new SmartRefElementPointer[collection.size()]);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    cleanup();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "EntryPointsManager";
  }

  private void validateEntryPoints() {
    Collection<SmartRefElementPointer> collection = myFQNameToSmartEntryPointRef.values();
    SmartRefElementPointer[] entries = collection.toArray(new SmartRefElementPointer[collection.size()]);
    for (SmartRefElementPointer entry : entries) {
      RefElement refElement = (RefElement)entry.getRefElement();
      if (refElement != null && !refElement.isValid()) {
        myFQNameToSmartEntryPointRef.remove(entry.getFQName());
      }
    }
  }

  public void cleanup() {
    purgeTemporaryEntryPoints();
    Collection<SmartRefElementPointer> entries = myFQNameToSmartEntryPointRef.values();
    for (SmartRefElementPointer entry : entries) {
      entry.freeReference();
    }
  }

  public boolean isAddNonJavaEntries() {
    return myAddNonJavaEntries;
  }

  public void addAllPersistentEntries(EntryPointsManagerImpl manager) {
    myFQNameToSmartEntryPointRef.putAll(manager.myFQNameToSmartEntryPointRef);
  }

  public void convert(Element element) {
    List content = element.getChildren();
    for (final Object aContent : content) {
      Element entryElement = (Element)aContent;
      if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
        String fqName = entryElement.getAttributeValue(SmartRefElementPointerImpl.FQNAME_ATTR);
        final String type = entryElement.getAttributeValue(SmartRefElementPointerImpl.TYPE_ATTR);
        if (Comparing.strEqual(type, RefJavaManager.METHOD)) {

          int spaceIdx = fqName.indexOf(' ');
          int lastDotIdx = fqName.lastIndexOf('.');
          boolean notype = false;

          int parenIndex = fqName.indexOf('(');

          while (lastDotIdx > parenIndex) lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);

          if (spaceIdx < 0 || spaceIdx + 1 > lastDotIdx || spaceIdx > parenIndex) {
            notype = true;
          }

          final String className = fqName.substring(notype ? 0 : spaceIdx + 1, lastDotIdx);
          final String methodSignature =
              notype ? fqName.substring(lastDotIdx + 1) : fqName.substring(0, spaceIdx) + ' ' + fqName.substring(lastDotIdx + 1);

          fqName = className + " " + methodSignature;
        }
        else if (Comparing.strEqual(type, RefJavaManager.FIELD)) {
          final int lastDotIdx = fqName.lastIndexOf('.');
          if (lastDotIdx > 0 && lastDotIdx < fqName.length() - 2) {
            String className = fqName.substring(0, lastDotIdx);
            String fieldName = fqName.substring(lastDotIdx + 1);
            fqName = className + " " + fieldName;
          }
          else {
            continue;
          }
        }
        SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(type, fqName);
        myFQNameToSmartEntryPointRef.put(entryPoint.getFQName(), entryPoint);
      }
    }
  }

  public void setAddNonJavaEntries(final boolean addNonJavaEntries) {
    myAddNonJavaEntries = addNonJavaEntries;
  }
}

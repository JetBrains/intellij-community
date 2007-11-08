package com.intellij.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class RecentsManager implements JDOMExternalizable, ProjectComponent {
  private final Map<String, LinkedList<String>> myMap = new HashMap<String, LinkedList<String>>();

  private int myRecentsNumberToKeep = 5;
  @NonNls private static final String KEY_ELEMENT_NAME = "key";
  @NonNls private static final String RECENT_ELEMENT_NAME = "recent";
  @NonNls protected static final String NAME_ATTR = "name";

  public static RecentsManager getInstance(Project project) {
    return project.getComponent(RecentsManager.class);
  }

  @Nullable
  public List<String> getRecentEntries(String key) {
    return myMap.get(key);
  }

  public void registerRecentEntry(String key, String recentEntry) {
    LinkedList<String> recents = myMap.get(key);
    if (recents == null) {
      recents = new LinkedList<String>();
      myMap.put(key, recents);
    }

    add(recents, recentEntry);
  }

  private void add(final LinkedList<String> recentEntrues, final String newEntry) {
    final int oldIndex = recentEntrues.indexOf(newEntry);
    if (oldIndex >= 0) {
      recentEntrues.remove(oldIndex);
    }
    else if (recentEntrues.size() == myRecentsNumberToKeep) {
      recentEntrues.removeLast();
    }

    recentEntrues.addFirst(newEntry);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myMap.clear();
    final List keyElements = element.getChildren(KEY_ELEMENT_NAME);
    for (Iterator iterator = keyElements.iterator(); iterator.hasNext();) {
      Element keyElement = (Element)iterator.next();
      final String key = keyElement.getAttributeValue(NAME_ATTR);
      LinkedList<String> recents = new LinkedList<String>();
      final List children = keyElement.getChildren(RECENT_ELEMENT_NAME);
      for (Iterator<Element> iterator1 = children.iterator(); iterator1.hasNext();) {
        recents.addLast(iterator1.next().getAttributeValue(NAME_ATTR));
      }

      myMap.put(key, recents);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Set<Map.Entry<String, LinkedList<String>>> entries = myMap.entrySet();
    for (Map.Entry<String, LinkedList<String>> entry : entries) {
      final Element keyElement = new Element(KEY_ELEMENT_NAME);
      keyElement.setAttribute(NAME_ATTR, entry.getKey());
      final LinkedList<String> recents = entry.getValue();
      for (String recent : recents) {
        final Element recentElement = new Element(RECENT_ELEMENT_NAME);
        recentElement.setAttribute(NAME_ATTR, recent);
        keyElement.addContent(recentElement);
      }
      element.addContent(keyElement);
    }
  }

  public void setRecentsNumberToKeep(final int recentsNumberToKeep) {
    myRecentsNumberToKeep = recentsNumberToKeep;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "RecentsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}

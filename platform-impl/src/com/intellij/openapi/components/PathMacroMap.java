/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public abstract class PathMacroMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.PathMacroMap");
  protected final Map<String, String> myMacroMap;

  protected PathMacroMap() {
    myMacroMap = new LinkedHashMap<String,String>();
  }

  public void putAll(PathMacroMap pathMacroMap) {
    putAll(pathMacroMap.myMacroMap);
  }

  public void putAll(Map<String, String> macroMap) {
    myMacroMap.putAll(macroMap);
  }

  public void put(String fromText, String toText) {
    myMacroMap.put(fromText, toText);
  }

  @SuppressWarnings({"WeakerAccess"})
  public abstract String substitute(String text, boolean caseSensitive, @Nullable final Set<String> usedMacros);

  public final void substitute(Element e, boolean caseSensitive, @Nullable final Set<String> usedMacros) {
    substitute(e, caseSensitive, usedMacros, false);
  }

  public final void substitute(Element e, boolean caseSensitive, @Nullable final Set<String> usedMacros, final boolean recursively) {
    List content = e.getContent();
    for (Object child : content) {
      if (child instanceof Element) {
        Element element = (Element)child;

        //mike
        //dirty hack: do not substitute macroses in path macroses declarations.
        //I can't find a way to disable macro saving in one component (yet).

        if (element.getName().equals("macro") &&
            element.getAttributes().size() == 2 &&
            element.getAttributeValue("name") != null &&
            element.getAttributeValue("value") != null &&
            element.getChildren().isEmpty()) continue;

        substitute(element, caseSensitive, usedMacros, recursively);
      }
      else if (child instanceof Text) {
        Text t = (Text)child;
        t.setText(recursively ? substituteRecursively(t.getText(), caseSensitive, usedMacros) : substitute(t.getText(), caseSensitive, usedMacros));
      }
      else if (child instanceof Comment) {
        /*do not substitute in comments
        Comment c = (Comment)child;
        c.setText(substitute(c.getText(), caseSensitive, usedMacros));
        */
      }
      else {
        LOG.error("Wrong content: " + child.getClass());
      }
    }

    List attributes = e.getAttributes();
    for (final Object attribute1 : attributes) {
      Attribute attribute = (Attribute)attribute1;
      attribute.setValue(recursively? substituteRecursively(attribute.getValue(), caseSensitive, usedMacros) : substitute(attribute.getValue(), caseSensitive, usedMacros));
    }
  }

  public String substituteRecursively(String text, boolean caseSensitive, Set<String> usedMacros) {
    return substitute(text, caseSensitive, usedMacros);
  }

  public int size() {
    return myMacroMap.size();
  }

  protected Set<Map.Entry<String, String>> entries() {
    return myMacroMap.entrySet();
  }

  protected Set<String> keySet() {
    return myMacroMap.keySet();
  }

  public String get(String key) {
    return myMacroMap.get(key);
  }

  public static String quotePath(String path) {
    path = path.replace(File.separatorChar, '/');
    //path = StringUtil.replace(path, "&", "&amp;");
    return path;
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }
}

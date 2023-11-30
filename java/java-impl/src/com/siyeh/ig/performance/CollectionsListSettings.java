// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

/**
 * @author Dmitry Batkovich
 */
public abstract class CollectionsListSettings implements OptionContainer {
  @NonNls
  public static final SortedSet<String> DEFAULT_COLLECTION_LIST;

  static {
    final SortedSet<String> set = new TreeSet<>();
    set.add("java.util.concurrent.ConcurrentHashMap");
    set.add("java.util.concurrent.PriorityBlockingQueue");
    set.add("java.util.ArrayDeque");
    set.add("java.util.ArrayList");
    set.add("java.util.HashMap");
    set.add("java.util.Hashtable");
    set.add("java.util.HashSet");
    set.add("java.util.IdentityHashMap");
    set.add("java.util.LinkedHashMap");
    set.add(CommonClassNames.JAVA_UTIL_LINKED_HASH_SET);
    set.add("java.util.PriorityQueue");
    set.add("java.util.Vector");
    set.add("java.util.WeakHashMap");
    DEFAULT_COLLECTION_LIST = Collections.unmodifiableSortedSet(set);
  }

  private final List<String> myCollectionClassesRequiringCapacity;

  public CollectionsListSettings() {
    myCollectionClassesRequiringCapacity = new ArrayList<>(getDefaultSettings());
  }

  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myCollectionClassesRequiringCapacity.clear();
    myCollectionClassesRequiringCapacity.addAll(getDefaultSettings());
    for (Element classElement : node.getChildren("cls")) {
      final String className = classElement.getText();
      if (classElement.getAttributeValue("remove", Boolean.FALSE.toString()).equals(Boolean.TRUE.toString())) {
        myCollectionClassesRequiringCapacity.remove(className);
      }
      else {
        myCollectionClassesRequiringCapacity.add(className);
      }
    }
  }

  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    final Collection<String> defaultToRemoveSettings = new HashSet<>(getDefaultSettings());
    defaultToRemoveSettings.removeAll(myCollectionClassesRequiringCapacity);

    final Set<String> toAdd = new HashSet<>(myCollectionClassesRequiringCapacity);
    toAdd.removeAll(getDefaultSettings());

    for (String className : defaultToRemoveSettings) {
      node.addContent(new Element("cls").setText(className).setAttribute("remove", Boolean.TRUE.toString()));
    }
    for (String className : toAdd) {
      node.addContent(new Element("cls").setText(className));
    }
  }

  protected abstract Collection<String> getDefaultSettings();

  public Collection<String> getCollectionClassesRequiringCapacity() {
    return myCollectionClassesRequiringCapacity;
  }

  public @NotNull OptPane getOptionPane() {
    return pane(stringList("myCollectionClassesRequiringCapacity",
                           QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.options.label"),
                           new JavaClassValidator().withTitle(
                            QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.options.dialog.title"))));
  }
}

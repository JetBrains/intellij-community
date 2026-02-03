// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AutomaticUsageRenamer<T> {
  private final String myOldName;
  private final String myNewName;
  private final Map<T, String> myRenames = new LinkedHashMap<>();
  private final List<T> myElements = new ArrayList<>();

  protected AutomaticUsageRenamer(List<? extends T> renamedElements, String oldName, String newName) {
    myOldName = oldName;
    myNewName = newName;
    List<T> elements = new ArrayList<>(renamedElements);
    elements.sort((o1, o2) -> {
      return getName(o1).compareTo(getName(o2));
    });
    for (T element : elements) {
      String suggestedNewName = suggestName(element);
      if (!getName(element).equals(suggestedNewName)) {
        myElements.add(element);
        setRename(element, suggestedNewName);
      }
    }
  }

  public boolean isEmpty() {
    return myRenames.isEmpty();
  }

  protected String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  protected boolean isChecked(T element) {
    return myRenames.containsKey(element);
  }

  protected boolean isCheckedInitially(T element) {
    return false;
  }

  public List<? extends T> getElements() {
    return myElements;
  }

  public String getNewElementName(T element) {
    return myRenames.get(element);
  }

  public Map<? extends T,String> getRenames() {
    return myRenames;
  }

  public void setRename(T element, @NotNull String replacement) {
    myRenames.put(element, replacement);
  }

  public void doNotRename(T element) {
    myRenames.remove(element);
  }

  public final void doRename() throws IncorrectOperationException {
  }

  protected abstract String suggestName(T element);

  protected abstract @NlsSafe String getName(T element);

  public abstract @NlsContexts.DialogTitle String getDialogTitle();

  public abstract @Nls String getDialogDescription();

  public abstract String getEntityName();
}


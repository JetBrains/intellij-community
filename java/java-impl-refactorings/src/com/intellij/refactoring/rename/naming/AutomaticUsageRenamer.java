// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usages.RenameableUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class AutomaticUsageRenamer<T> {
  private final String myOldName;
  private final String myNewName;
  private final Map<T, String> myRenames = new LinkedHashMap<>();
  private final List<T> myElements = new ArrayList<>();
  private final Map<T, List<RenameableUsage>> myReferences = new HashMap<>();

  protected AutomaticUsageRenamer(List<? extends T> renamedElements, String oldName, String newName) {
    myOldName = oldName;
    myNewName = newName;
    List<T> elements = new ArrayList<>(renamedElements);
    elements.sort((o1, o2) -> {
      int i = StringUtil.compare(getSourceName(o1), getSourceName(o2), false);
      if (i != 0) return i;
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

  public boolean hasAnythingToRename() {
    for (final String s : myRenames.values()) {
      if (s != null) return true;
    }
    return false;
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

  protected boolean isNameAlreadySuggested(String newName) {
    return myRenames.containsValue(newName);
  }

  public List<? extends T> getElements() {
    return myElements;
  }

  /**
   * Element source, path. For example, package. Taken into account while sorting.
   */
  @Nullable
  @NlsSafe
  public String getSourceName(T element) {
    return null;
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

  @Nullable
  public @NlsContexts.Tooltip String getErrorText(T element) {
    return null;
  }

  public final void doRename() throws IncorrectOperationException {
    for (final Map.Entry<T, List<RenameableUsage>> entry : myReferences.entrySet()) {
      final T element = entry.getKey();
      final String newName = getNewElementName(element);
      doRenameElement(element);
      for (final RenameableUsage usage : entry.getValue()) {
        usage.rename(newName);
      }
    }
  }

  protected abstract void doRenameElement(T element) throws IncorrectOperationException;

  protected abstract String suggestName(T element);

  protected abstract @NlsSafe String getName(T element);

  public abstract @NlsContexts.DialogTitle String getDialogTitle();

  public abstract @Nls String getDialogDescription();

  public abstract String getEntityName();
}


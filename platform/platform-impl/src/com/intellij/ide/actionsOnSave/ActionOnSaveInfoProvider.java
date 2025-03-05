// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Allows adding UI (CheckBox, comment, ActionLink and more) to the 'Actions on Save' page in Settings (Preferences).<br/>
 * <code>ActionOnSaveInfoProvider</code> and {@link ActionOnSaveInfo} are mostly about the UI / UX on the 'Actions on Save' page. They
 * don't provide any engine that runs each particular 'action on save'. As an engine example, see {@link ActionsOnSaveFileDocumentManagerListener}
 * or implement any custom engine that works the best for each specific 'action on save'.
 * <br/><br/>
 * Please use `order` attribute when registering {@link ActionOnSaveInfoProvider}s and be as specific as possible. Some plugins may be
 * disabled, so it's recommended to use 'before' and 'after' anchors relative to all other known extensions. The order of the checkboxes
 * on the 'Actions on Save' page should reflect the real order of the performed actions.
 */
public abstract class ActionOnSaveInfoProvider {

  public static final ExtensionPointName<ActionOnSaveInfoProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.actionOnSaveInfoProvider");

  static ArrayList<ActionOnSaveInfo> getAllActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    ArrayList<ActionOnSaveInfo> infos = new ArrayList<>();
    for (ActionOnSaveInfoProvider provider : EP_NAME.getExtensionList()) {
      infos.addAll(provider.getActionOnSaveInfos(context));
    }
    return infos;
  }

  /**
   * This method is called each time when the 'Actions on Save' page in Settings (Preferences) becomes visible. If a user moves
   * to a different page in Settings (Preferences) and then back to the 'Actions on Save' page, this method will be called once again.
   * <br/><br/>
   * <code>getActionOnSaveInfos()</code> implementations should return new instances of {@link ActionOnSaveInfo} each time.
   * <br/><br/>
   * <code>ActionOnSaveInfo</code> implementations should get understanding about their current state based on the provided {@link ActionOnSaveContext}.
   *
   * @see ActionOnSaveInfo#ActionOnSaveInfo(ActionOnSaveContext)
   * @see ActionOnSaveContext
   */
  protected abstract @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context);

  /**
   * Strings that help to find 'Actions on Save' page when using 'Find Action' or 'Search Everywhere' or when using a search field in
   * Settings (Preferences). Typically, implementations return a singleton list with {@link ActionOnSaveInfo#getActionOnSaveName()}.
   */
  public Collection<String> getSearchableOptions() {
    return Collections.emptyList();
  }
}

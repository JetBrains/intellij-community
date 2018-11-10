// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.components.ServiceManager;

import java.util.List;
import java.util.Set;

/**
 * Manages abbreviations for actions. An abbreviation is an alias for the action name
 * which the user can enter in the Goto Action/Search Everywhere popups.
 *
 * @author Konstantin Bulenkov
 * @since 13
 */
public abstract class AbbreviationManager {
  public static AbbreviationManager getInstance() {
    return ServiceManager.getService(AbbreviationManager.class);
  }

  public abstract Set<String> getAbbreviations();

  public abstract Set<String> getAbbreviations(String actionId);

  public abstract List<String> findActions(String abbreviation);

  public abstract void register(String abbreviation, String actionId);

  public abstract void remove(String abbreviation, String actionId);

  public abstract void removeAllAbbreviations(String actionId);
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement;

import org.jetbrains.annotations.NonNls;

public final class ArrangementConstants {

  public static final @NonNls String ACTION_GROUP_GROUPING_RULES_CONTROL_TOOLBAR = "Arrangement.Rule.Group.Control.ToolBar";
  public static final @NonNls String GROUPING_RULES_CONTROL_TOOLBAR_PLACE        = "Arrangement.Rule.Group.Control.ToolBar.Place";

  public static final @NonNls String ACTION_GROUP_MATCHING_RULES_CONTEXT_MENU    = "Arrangement.Rule.Match.Control.Context.Menu";
  public static final @NonNls String ACTION_GROUP_MATCHING_RULES_CONTROL_TOOLBAR = "Arrangement.Rule.Match.Control.ToolBar";
  public static final @NonNls String MATCHING_RULES_CONTROL_TOOLBAR_PLACE        = "Arrangement.Rule.Match.Control.ToolBar.Place";
  public static final @NonNls String MATCHING_RULES_CONTROL_PLACE                = "Arrangement.Rule.Match.Control.Place";
  public static final @NonNls String ALIAS_RULE_CONTEXT_MENU                     = "Arrangement.Alias.Rule.Context.Menu";
  public static final @NonNls String ALIAS_RULE_CONTROL_TOOLBAR                  = "Arrangement.Alias.Rule.ToolBar";
  public static final @NonNls String ALIAS_RULE_CONTROL_TOOLBAR_PLACE            = "Arrangement.Alias.Rule.ToolBar.Place";
  public static final @NonNls String ALIAS_RULE_CONTROL_PLACE                    = "Arrangement.Alias.Rule.Control.Place";

  public static final int HORIZONTAL_PADDING    = 8;
  public static final int VERTICAL_PADDING      = 4;
  public static final int HORIZONTAL_GAP        = 5;
  public static final int VERTICAL_GAP          = 3;
  public static final int CALLOUT_BORDER_HEIGHT = 10;
  public static final int BORDER_ARC_SIZE       = 12;

  public static final int ANIMATION_STEPS_TIME_GAP_MILLIS = 40;
  public static final int TEXT_UPDATE_DELAY_MILLIS        = 1000;

  public static final boolean LOG_RULE_MODIFICATION = Boolean.parseBoolean(System.getProperty("log.arrangement.rule.modification"));

  private ArrangementConstants() {
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.arrangement;

import org.jetbrains.annotations.NonNls;

/**
 * @author Denis Zhdanov
 */
public final class ArrangementConstants {

  @NonNls public static final String ACTION_GROUP_GROUPING_RULES_CONTROL_TOOLBAR = "Arrangement.Rule.Group.Control.ToolBar";
  @NonNls public static final String GROUPING_RULES_CONTROL_TOOLBAR_PLACE        = "Arrangement.Rule.Group.Control.ToolBar.Place";

  @NonNls public static final String ACTION_GROUP_MATCHING_RULES_CONTEXT_MENU    = "Arrangement.Rule.Match.Control.Context.Menu";
  @NonNls public static final String ACTION_GROUP_MATCHING_RULES_CONTROL_TOOLBAR = "Arrangement.Rule.Match.Control.ToolBar";
  @NonNls public static final String MATCHING_RULES_CONTROL_TOOLBAR_PLACE        = "Arrangement.Rule.Match.Control.ToolBar.Place";
  @NonNls public static final String MATCHING_RULES_CONTROL_PLACE                = "Arrangement.Rule.Match.Control.Place";
  @NonNls public static final String ALIAS_RULE_CONTEXT_MENU                     = "Arrangement.Alias.Rule.Context.Menu";
  @NonNls public static final String ALIAS_RULE_CONTROL_TOOLBAR                  = "Arrangement.Alias.Rule.ToolBar";
  @NonNls public static final String ALIAS_RULE_CONTROL_TOOLBAR_PLACE            = "Arrangement.Alias.Rule.ToolBar.Place";
  @NonNls public static final String ALIAS_RULE_CONTROL_PLACE                    = "Arrangement.Alias.Rule.Control.Place";

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

/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;

/**
 * @author Denis Zhdanov
 * @since 8/13/12 11:48 AM
 */
public class ArrangementConstants {

  @NonNls public static final String ACTION_GROUP_RULE_EDITOR_CONTEXT_MENU = "Arrangement.RuleEditor.Context.Menu";
  @NonNls public static final String ACTION_GROUP_RULE_EDITOR_TOOL_WINDOW  = "Arrangement.RuleEditor.ToolWIndow";

  @NonNls public static final String RULE_EDITOR_PLACE             = "Arrangement.RuleEditor.Place";
  @NonNls public static final String RULE_EDITOR_TOOL_WINDOW_PLACE = "Arrangement.RuleEditor.ToolWindow.Place";
  @NonNls public static final String RULE_TREE_PLACE               = "Arrangement.RuleTree.Place";

  public static final int HORIZONTAL_PADDING    = 8;
  public static final int VERTICAL_PADDING      = 4;
  public static final int HORIZONTAL_GAP        = 5;
  public static final int VERTICAL_GAP          = 3;
  public static final int CALLOUT_BORDER_HEIGHT = 10;
  public static final int BORDER_ARC_SIZE       = 12;

  public static final int ANIMATION_ITERATION_PIXEL_STEP  = 5;
  public static final int ANIMATION_STEPS_TIME_GAP_MILLIS = 40;

  public static final boolean LOG_RULE_MODIFICATION = Boolean.parseBoolean(System.getProperty("log.arrangement.rule.modification"));

  public static final DataKey<Runnable> NEW_RULE_FUNCTION_KEY = DataKey.create("Arrangement.Rule.Function.New");
  public static final DataKey<Runnable> REMOVE_RULE_FUNCTION_KEY = DataKey.create("Arrangement.Rule.Function.Remove");

  public static final DataKey<NotNullFunction<Boolean/* move up? */, Boolean/* is enabled */>> UPDATE_MOVE_RULE_FUNCTION_KEY
    = DataKey.create("Arrangement.Rule.Function.Update.Move");
  public static final DataKey<Consumer<Boolean/* move up? */>> MOVE_RULE_FUNCTION_KEY = DataKey.create("Arrangement.Rule.Function.Move");
  
  private ArrangementConstants() {
  }
}

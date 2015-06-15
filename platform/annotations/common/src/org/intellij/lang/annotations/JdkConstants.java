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
package org.intellij.lang.annotations;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Calendar;
import java.util.regex.Pattern;

@SuppressWarnings("EmptyClass")
public class JdkConstants {
  @MagicConstant(intValues = {SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.LEADING, SwingConstants.TRAILING})
  public @interface HorizontalAlignment {}

  @MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT, FlowLayout.LEADING, FlowLayout.TRAILING})
  public @interface FlowLayoutAlignment {}

  @MagicConstant(valuesFromClass = Cursor.class)
  public @interface CursorType {}

  @MagicConstant(flagsFromClass = InputEvent.class)
  public @interface InputEventMask {}

  @MagicConstant(intValues = {Adjustable.HORIZONTAL, Adjustable.VERTICAL})
  public @interface AdjustableOrientation {}

  @MagicConstant(intValues = {Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST, Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER})
  public @interface CalendarMonth {}

  @MagicConstant(flagsFromClass = Pattern.class)
  public @interface PatternFlags {}

  @MagicConstant(valuesFromClass = BoxLayout.class)
  public @interface BoxLayoutAxis {}

  @MagicConstant(valuesFromClass = ListSelectionModel.class)
  public @interface ListSelectionMode{}

  @MagicConstant(valuesFromClass = TreeSelectionModel.class)
  public @interface TreeSelectionMode{}

  @MagicConstant(flags = {Font.PLAIN, Font.BOLD, Font.ITALIC})
  public @interface FontStyle {}

  @MagicConstant(intValues = {TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.LEFT, TitledBorder.CENTER, TitledBorder.RIGHT, TitledBorder.LEADING, TitledBorder.TRAILING})
  public @interface TitledBorderJustification {}

  @MagicConstant(intValues = {TitledBorder.DEFAULT_POSITION, TitledBorder.ABOVE_TOP, TitledBorder.TOP, TitledBorder.BELOW_TOP, TitledBorder.ABOVE_BOTTOM, TitledBorder.BOTTOM, TitledBorder.BELOW_BOTTOM})
  public @interface TitledBorderTitlePosition{}

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
  public @interface TabPlacement{}

  @MagicConstant(intValues = {JTabbedPane.WRAP_TAB_LAYOUT, JTabbedPane.SCROLL_TAB_LAYOUT})
  public @interface TabLayoutPolicy{}
}

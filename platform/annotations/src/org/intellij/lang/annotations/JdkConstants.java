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

import javax.naming.event.NamingEvent;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.lang.reflect.Member;
import java.util.Calendar;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

@SuppressWarnings("EmptyClass")
public class JdkConstants {
  @MagicConstant(flagsFromClass = java.lang.reflect.Modifier.class)
  public @interface Modifier {}

  @MagicConstant(intValues = {SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.LEADING, SwingConstants.TRAILING})
  public @interface HorizontalAlignment {}

  @MagicConstant(intValues = {FlowLayout.LEFT, FlowLayout.CENTER, FlowLayout.RIGHT, FlowLayout.LEADING, FlowLayout.TRAILING})
  public @interface FlowLayoutAlignment {}

  @MagicConstant(intValues = {BasicStroke.CAP_BUTT, BasicStroke.CAP_ROUND, BasicStroke.CAP_SQUARE})
  public @interface BasicStrokeCap {}

  @MagicConstant(intValues = {BasicStroke.JOIN_BEVEL, BasicStroke.JOIN_MITER, BasicStroke.JOIN_ROUND})
  public @interface BasicStrokeJoin {}

  @MagicConstant(valuesFromClass = Cursor.class)
  public @interface CursorType {}

  @MagicConstant(flagsFromClass = InputEvent.class)
  public @interface InputEventMask {}

  @MagicConstant(intValues = {Label.LEFT, Label.CENTER, Label.RIGHT})
  public @interface LabelAlignment {}

  @MagicConstant(intValues = {Adjustable.HORIZONTAL, Adjustable.VERTICAL})
  public @interface AdjustableOrientation {}

  @MagicConstant(intValues = {Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST, Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER })
  public @interface CalendarMonth {}

  @MagicConstant(flagsFromClass = Pattern.class)
  public @interface PatternFlags {}

  @MagicConstant(valuesFromClass = ZipEntry.class)
  public @interface ZipEntryMethod {}

  @MagicConstant(valuesFromClass = BoxLayout.class)
  public @interface BoxLayoutAxis {}

  @MagicConstant(intValues = {JComponent.WHEN_FOCUSED,JComponent.WHEN_IN_FOCUSED_WINDOW,JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT })
  public @interface JComponentCondition {}

  @MagicConstant(intValues = {JOptionPane.INFORMATION_MESSAGE,JOptionPane.WARNING_MESSAGE,JOptionPane.ERROR_MESSAGE,JOptionPane.QUESTION_MESSAGE,JOptionPane.PLAIN_MESSAGE })
  public @interface JOptionPaneMessageType {}

  @MagicConstant(intValues = {JOptionPane.DEFAULT_OPTION, JOptionPane.YES_NO_OPTION, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.OK_CANCEL_OPTION})
  public @interface JOptionPaneOptionType {}

  @MagicConstant(intValues = {JSplitPane.HORIZONTAL_SPLIT,JSplitPane.VERTICAL_SPLIT})
  public @interface JSplitPaneOrientation {}

  @MagicConstant(flags = {HierarchyEvent.PARENT_CHANGED,HierarchyEvent.DISPLAYABILITY_CHANGED,HierarchyEvent.SHOWING_CHANGED})
  public @interface HierarchyEventChangedFlags {}

  @MagicConstant(intValues = {ItemEvent.SELECTED, ItemEvent.DESELECTED})
  public @interface ItemEventStateChange {}

  @MagicConstant(intValues = {JFileChooser.FILES_ONLY, JFileChooser.DIRECTORIES_ONLY, JFileChooser.FILES_AND_DIRECTORIES})
  public @interface JFileChooserFileSelectionMode{}

  @MagicConstant(valuesFromClass = ListSelectionModel.class)
  public @interface ListSelectionMode{}

  @MagicConstant(valuesFromClass = TreeSelectionModel.class)
  public @interface TreeSelectionMode{}

  @MagicConstant(intValues = {BevelBorder.RAISED, BevelBorder.LOWERED})
  public @interface BevelBorderType {}

  @MagicConstant(intValues = {EtchedBorder.RAISED, EtchedBorder.LOWERED})
  public @interface EtchedBorderType {}

  @MagicConstant(intValues = {TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.LEFT, TitledBorder.CENTER, TitledBorder.RIGHT, TitledBorder.LEADING, TitledBorder.TRAILING})
  public @interface TitledBorderJustification {}

  @MagicConstant(intValues = {TitledBorder.ABOVE_TOP, TitledBorder.TOP, TitledBorder.BELOW_TOP, TitledBorder.ABOVE_BOTTOM, TitledBorder.BOTTOM, TitledBorder.BELOW_BOTTOM, TitledBorder.DEFAULT_POSITION})
  public @interface TitledBorderTitlePosition {}

  @MagicConstant(intValues = {AdjustmentEvent.UNIT_INCREMENT, AdjustmentEvent.UNIT_DECREMENT, AdjustmentEvent.BLOCK_INCREMENT, AdjustmentEvent.BLOCK_DECREMENT, AdjustmentEvent.TRACK})
  public @interface AdjustmentEventType {}

  @MagicConstant(intValues = {MouseWheelEvent.WHEEL_UNIT_SCROLL, MouseWheelEvent.WHEEL_BLOCK_SCROLL})
  public @interface MouseWheelEventType {}

  @MagicConstant(intValues = {Arc2D.OPEN, Arc2D.CHORD, Arc2D.PIE})
  public @interface Arc2DType {}

  @MagicConstant(intValues = {TableModelEvent.INSERT, TableModelEvent.UPDATE, TableModelEvent.DELETE})
  public @interface TableModelEventType {}

  @MagicConstant(valuesFromClass = ListDataEvent.class)
  public @interface ListDataEventType {}

  @MagicConstant(valuesFromClass = NamingEvent.class)
  public @interface NamingEventType {}

  @MagicConstant(valuesFromClass = Member.class)
  public @interface SecurityManagerMemberAccess {}


}

package com.michaelbaranov.microba.demo;

import com.michaelbaranov.microba.calendar.*;
import com.michaelbaranov.microba.common.AbstractPolicy;
import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradienteditor.GradientEditor;
import com.michaelbaranov.microba.marker.MarkerBar;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Demo extends JApplet {

  static MarkerBar bar;

  static BoundedTableModel model;

  private static JPanel panel;

  public static void main(String[] s) {
    run();
  }

  private static void run() {
    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.getContentPane().setLayout(new BorderLayout());

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("CalendarPane", buildCalendarPaneTab());
     tabs.addTab("Gradient", buildGradientTab());
     tabs.addTab("CellEditor", buildCellEditorTab());
     tabs.addTab("DatePicker", buildDatePickerTab());

    frame.getContentPane().add(tabs, BorderLayout.CENTER);
    frame.setSize(500, 300);
    frame.setVisible(true);

  }

  //
  // public void init() {
  // super.init();
  // System.out.println("init");
  // panel = new JPanel();
  // panel.setLayout(new BorderLayout());
  //
  // JTabbedPane tabs = new JTabbedPane();
  // tabs.addTab("CalendarPane", buildCalendarPaneTab());
  // tabs.addTab("Gradient", buildGradientTab());
  // tabs.addTab("CellEditor", buildCellEditorTab());
  // tabs.addTab("DatePicker", buildDatePickerTab());
  //
  // panel.add(tabs, BorderLayout.CENTER);
  // this.setContentPane(panel);
  // }

  @Override
  public void start() {
    System.out.println("start");
    super.start();
  }

  @Override
  public void stop() {
    System.out.println("stop");
    super.stop();
  }

  private static class Hol extends AbstractPolicy implements HolidayPolicy {

    @Override
    public String getHollidayName(Object source, Calendar date) {
      return null;
    }

    @Override
    public boolean isHolliday(Object source, Calendar date) {
      return false;
    }

    @Override
    public boolean isWeekend(Object source, Calendar date) {
      return date.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
    }

  }

  private static JComponent buildCalendarPaneTab() {
    JPanel panel = new JPanel();
    final CalendarPane calendarPane = new CalendarPane();

//    calendarPane.setEnabled(false);
    calendarPane.setHolidayPolicy(new Hol());

    Map<String, Color> ov = new HashMap<>();

    ov.put(CalendarPane.COLOR_CALENDAR_GRID_FOREGROUND_ENABLED,
        Color.ORANGE);
    
    calendarPane.setColorOverrideMap(ov);

    try {
      calendarPane.setDate(new Date());
    } catch (PropertyVetoException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    calendarPane.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        System.out.println("CalendarPane:" + calendarPane.getDate());

      }
    });

    panel.setLayout(new GridBagLayout());
    panel.add(calendarPane, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
        GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(
            5, 5, 5, 5), 0, 0));

    return panel;

  }

  private static JComponent buildDatePickerTab() {
    JPanel panel = new JPanel();
    final DatePicker datePicker = new DatePicker();
    // datePicker.setDateFormat(new SimpleDateFormat("HH dd yyyy"));
    datePicker.setDateFormat(DateFormat.getDateTimeInstance());
    // datePicker.setStripTime(false);
    datePicker.setEnabled(false);
    datePicker.setKeepTime(true);
    datePicker.setStripTime(false);
    datePicker.setShowNumberOfWeek(true);
    // datePicker.setEnabled(false);
    // datePicker.setPickerStyle(DatePicker.PICKER_STYLE_BUTTON);
    // datePicker.showButtonOnly(false);
    // datePicker.setToolTipText("hello!!!!");
    // datePicker.setShowNumberOfWeek(true);
    
    Map<String, Color> ov = new HashMap<>();

    ov.put(CalendarColors.COLOR_CALENDAR_GRID_FOREGROUND_ENABLED,
           Color.ORANGE);
    
    datePicker.setColorOverrideMap(ov);

    panel.setLayout(new GridBagLayout());
    panel.add(datePicker, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
        GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(
            5, 5, 5, 5), 0, 0));

    datePicker.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        System.out.println("DatePicker:" + datePicker.getDate());

      }
    });

    return panel;

  }

  private static JComponent buildCellEditorTab() {
    JPanel panel = new JPanel();
    DatePicker datePicker = new DatePicker();

    datePicker.setDropdownFocusable(true);

    DatePickerCellEditor cellEditor = new DatePickerCellEditor(datePicker);
    cellEditor.setClickCountToStart(2);

    JTable table = new JTable(100, 3);
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    JScrollPane scrollPane = new JScrollPane(table);

    table.getColumnModel().getColumn(0).setCellEditor(cellEditor);

    panel.setLayout(new GridBagLayout());
    panel.add(scrollPane, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
            5, 5, 5, 5), 0, 0));
    return panel;

  }

  private static JComponent buildGradientTab() {
    JPanel panel = new JPanel();
    GradientBar gradient;

    GradientEditor editor;
    JColorChooser colorChooser;

    colorChooser = new JColorChooser();
    editor = new GradientEditor();

    // editor.setOrientation(SwingConstants.VERTICAL);

    ColorAdaptor adaptor = new ColorAdaptor(editor, colorChooser);

    panel.add(editor, BorderLayout.NORTH);
    panel.add(colorChooser, BorderLayout.CENTER);

    MarkerBar bar = new MarkerBar();
    bar.setDataModel(editor.getDataModel());
    bar.setPositionColumn(editor.getColorPositionColumn());
    bar.setColorColumn(editor.getColorColumn());
    bar.setFliped(true);
    bar.setOrientation(SwingConstants.VERTICAL);
    panel.add(bar, BorderLayout.EAST);
    return panel;
  }

  private static class ColorAdaptor implements ChangeListener,
      ListSelectionListener {

    private final GradientEditor editor;

    private final JColorChooser chooser;

    private ColorAdaptor(GradientEditor editor, JColorChooser chooser) {
      super();
      this.editor = editor;
      this.chooser = chooser;

      editor.getColorSelectionModel().addListSelectionListener(this);
      chooser.getSelectionModel().addChangeListener(this);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      int index = editor.getColorSelectionModel().getLeadSelectionIndex();

      Color c = (Color) editor.getDataModel().getValueAt(index,
          editor.getColorColumn());
      chooser.setColor(c);
    }

    @Override
    public void stateChanged(ChangeEvent e) {

      if (!editor.getColorSelectionModel().isSelectionEmpty()) {
        int index = editor.getColorSelectionModel()
            .getLeadSelectionIndex();

        editor.getDataModel().setValueAt(chooser.getColor(), index,
            editor.getColorColumn());
      }

    }

  }

}

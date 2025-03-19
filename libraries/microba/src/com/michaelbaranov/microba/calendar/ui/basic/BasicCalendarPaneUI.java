package com.michaelbaranov.microba.calendar.ui.basic;

import com.michaelbaranov.microba.calendar.CalendarPane;
import com.michaelbaranov.microba.calendar.HolidayPolicy;
import com.michaelbaranov.microba.calendar.VetoPolicy;
import com.michaelbaranov.microba.calendar.ui.CalendarPaneUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.*;

public class BasicCalendarPaneUI extends CalendarPaneUI implements
    PropertyChangeListener, FocusListener {

  protected static final String ESCAPE_KEY = "##CalendarPaneUI.escape##";

  protected static final String ENTER_KEY = "##CalendarPaneUI.enter##";

  protected CalendarPane peer;

  protected ClassicCalendarPanel classicPanel;

  protected ModernCalendarPanel modernPanel;

  protected AuxPanel auxPanel;

  protected CalendarGridPanel gridPanel;

  protected CalendarNumberOfWeekPanel numberOfWeekPanel;

  protected CalendarHeader headerPanel;

  protected Set<JComponent> focusableComponents = new HashSet<>();

  protected ComponentListener componentListener;

  public static ComponentUI createUI(JComponent c) {
    return new BasicCalendarPaneUI();
  }

  @Override
  public void installUI(JComponent component) {
    peer = (CalendarPane) component;
    createNestedComponents();
    addNestedComponents();
    installListeners();
    installKeyboardActions();
  }

  @Override
  public void uninstallUI(JComponent component) {
    uninstallKeyboardActions();
    uninstallListeners();
    removeNestedComponents();
    destroyNestedComponents();
    peer = null;
  }

  protected void uninstallKeyboardActions() {
    InputMap input = peer
        .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap action = peer.getActionMap();

    input.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    input.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

    action.remove(ENTER_KEY);
    action.remove(ESCAPE_KEY);

  }

  protected void installKeyboardActions() {
    InputMap input = peer
        .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap action = peer.getActionMap();

    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_KEY);
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESCAPE_KEY);
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pgupkey");
    input
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
            "pgdownkey");

    action.put(ENTER_KEY, new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        peer.commitEdit();
      }
    });
    action.put(ESCAPE_KEY, new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        peer.revertEdit();
      }
    });
    action.put("pgupkey", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        classicPanel.addMonth(1);
      }
    });
    action.put("pgdownkey", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        classicPanel.addMonth(-1);
      }
    });
  }

  protected void uninstallListeners() {
    peer.removePropertyChangeListener(this);
    peer.removeFocusListener(this);
  }

  protected void installListeners() {
    peer.addPropertyChangeListener(this);
    peer.addFocusListener(this);
  }

  protected void createNestedComponents() {
    Date baseDate = peer.getDate() == null ? new Date() : peer.getDate();

    classicPanel = new ClassicCalendarPanel(baseDate, peer.getLocale(),
        peer.getZone());
    modernPanel = new ModernCalendarPanel(baseDate, peer.getLocale(), peer
        .getZone());
    headerPanel = new CalendarHeader(peer, baseDate, peer.getLocale(), peer
        .getZone(), peer.getHolidayPolicy());

    auxPanel = new AuxPanel(peer.getLocale(), peer.getZone(), peer
        .getVetoPolicy(), peer.isShowTodayButton(), peer
        .isShowNoneButton(), peer.getResources());

    gridPanel = new CalendarGridPanel(peer, peer.getDate(), peer
        .getLocale(), peer.getZone(), peer.getVetoPolicy(), peer
        .getHolidayPolicy());

    numberOfWeekPanel = new CalendarNumberOfWeekPanel(peer.getDate(), peer
        .getLocale(), peer.getZone());

    focusableComponents.addAll(classicPanel.getFocusableComponents());
    focusableComponents.addAll(modernPanel.getFocusableComponents());
    focusableComponents.addAll(auxPanel.getFocusableComponents());
    focusableComponents.addAll(gridPanel.getFocusableComponents());
    focusableComponents.addAll(auxPanel.getFocusableComponents());

    componentListener = new ComponentListener();
    for (int i = 0; i < focusableComponents.size(); i++)
      ((JComponent) focusableComponents.toArray()[i])
          .addFocusListener(componentListener);

    gridPanel.addPropertyChangeListener(componentListener);
    modernPanel.addPropertyChangeListener(componentListener);
    classicPanel.addPropertyChangeListener(componentListener);
    auxPanel.addPropertyChangeListener(componentListener);
    
    classicPanel.setEnabled(peer.isEnabled());
    modernPanel.setEnabled(peer.isEnabled());
    headerPanel.setEnabled(peer.isEnabled());
    auxPanel.setEnabled(peer.isEnabled());
    numberOfWeekPanel.setEnabled(peer.isEnabled());
    gridPanel.setEnabled(peer.isEnabled());

  }

  protected void destroyNestedComponents() {
    gridPanel.removePropertyChangeListener(componentListener);
    modernPanel.removePropertyChangeListener(componentListener);
    classicPanel.removePropertyChangeListener(componentListener);
    auxPanel.removePropertyChangeListener(componentListener);
    componentListener = null;

    for (int i = 0; i < focusableComponents.size(); i++)
      ((JComponent) focusableComponents.toArray()[i])
          .removeFocusListener(componentListener);
    focusableComponents.clear();

    classicPanel = null;
    modernPanel = null;
    headerPanel = null;
    auxPanel = null;
    gridPanel = null;
    numberOfWeekPanel = null;

  }

  protected void addNestedComponents() {

    peer.removeAll();
    peer.setLayout(new GridBagLayout());
    if ((peer.getStyle() & CalendarPane.STYLE_CLASSIC) > 0) {
      peer.add(classicPanel, new GridBagConstraints(0, 0, 2, 1, 1, 0,
          GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0));
    } else {
      peer.add(modernPanel, new GridBagConstraints(0, 0, 2, 1, 1, 0,
          GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0));
    }
    peer.add(headerPanel, new GridBagConstraints(1, 1, 1, 1, 1, 0,
        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0));
    if (peer.isShowNumberOfWeek()) {
      peer.add(numberOfWeekPanel, new GridBagConstraints(0, 2, 1, 1, 0,
          1, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
          new Insets(0, 0, 0, 0), 0, 0));
    }
    peer.add(gridPanel, new GridBagConstraints(1, 2, 1, 1, 1, 1,
        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
            0, 0, 0, 0), 0, 0));
    if (peer.isShowTodayButton()) {
      peer.add(auxPanel, new GridBagConstraints(0, 3, 2, 1, 1, 0,
          GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
          new Insets(0, 0, 0, 0), 0, 0));
    }
    if (peer.isShowNumberOfWeek()) {
      // TODO:
    }
    peer.revalidate();
    peer.repaint();

  }

  protected void removeNestedComponents() {
    peer.removeAll();
  }

  protected void widgetDateChanged(Date date) {
    Date baseDate = date == null ? new Date() : date;

    headerPanel.setDate(baseDate);
    classicPanel.setDate(baseDate);
    modernPanel.setDate(baseDate);

    gridPanel.setBaseDate(baseDate);
    gridPanel.setDate(date);
    numberOfWeekPanel.setBaseDate(baseDate);
  }

  protected void widgetLocaleChanged(Locale newValue) {
    classicPanel.setLocale(newValue);
    modernPanel.setLocale(newValue);
    gridPanel.setLocale(newValue);
    headerPanel.setLocale(newValue);
    auxPanel.setLocale(newValue);
    numberOfWeekPanel.setLocale(newValue);
  }

  protected void widgetZoneChanged(TimeZone zone) {
    classicPanel.setZone(zone);
    modernPanel.setZone(zone);
    gridPanel.setZone(zone);
    headerPanel.setZone(zone);
    auxPanel.setZone(zone);
    numberOfWeekPanel.setZone(zone);
  }

  protected void widgetResourceChanged() {
    auxPanel.setResources(peer.getResources());
  }

  @Override
  public void commit() throws PropertyVetoException {
    peer.setDate(gridPanel.getDateToCommit());
  }

  @Override
  public void revert() {
    widgetDateChanged(peer.getDate());
  }

  @Override
  public void focusGained(FocusEvent e) {
    gridPanel.requestFocus(true);
  }

  @Override
  public void focusLost(FocusEvent e) {
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(CalendarPane.PROPERTY_NAME_DATE)) {
      widgetDateChanged((Date) evt.getNewValue());
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_LOCALE)) {
      widgetLocaleChanged((Locale) evt.getNewValue());
    } else if (evt.getPropertyName()
        .equals(CalendarPane.PROPERTY_NAME_ZONE)) {
      widgetZoneChanged((TimeZone) evt.getNewValue());
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_VETO_POLICY)) {
      gridPanel.setVetoPolicy((VetoPolicy) evt.getNewValue());
      auxPanel.setVetoModel((VetoPolicy) evt.getNewValue());
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_HOLIDAY_POLICY)) {
      gridPanel.setHolidayPolicy((HolidayPolicy) evt.getNewValue());
      headerPanel.setHolidayPolicy((HolidayPolicy) evt.getNewValue());
    } else if (evt.getPropertyName().equals("enabled")) {
      boolean value = ((Boolean) evt.getNewValue()).booleanValue();
      classicPanel.setEnabled(value);
      modernPanel.setEnabled(value);
      headerPanel.setEnabled(value);
      auxPanel.setEnabled(value);
      numberOfWeekPanel.setEnabled(value);
      gridPanel.setEnabled(value);
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_STYLE)) {
      addNestedComponents();
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_SHOW_TODAY_BTN)) {
      Boolean value = (Boolean) evt.getNewValue();
      auxPanel.setShowTodayBtn(value.booleanValue());
    } else if (evt.getPropertyName().equals(
      CalendarPane.PROPERTY_NAME_SHOW_NONE_BTN)) {
      boolean value = ((Boolean) evt.getNewValue()).booleanValue();
      auxPanel.setShowNoneButton(value);
    } else if (evt.getPropertyName().equals(
      CalendarPane.PROPERTY_NAME_SHOW_NUMBER_WEEK)) {
      addNestedComponents();
    } else if (evt.getPropertyName().equals("focusable")) {
      Boolean value = (Boolean) evt.getNewValue();
      classicPanel.setFocusable(value.booleanValue());
      modernPanel.setFocusable(value.booleanValue());
      gridPanel.setFocusable(value.booleanValue());
      auxPanel.setFocusable(value.booleanValue());
    } else if (evt.getPropertyName().equals(
        CalendarPane.PROPERTY_NAME_RESOURCES)) {
      widgetResourceChanged();
    }
  }

  protected class ComponentListener implements FocusListener,
      PropertyChangeListener {
    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(FocusEvent e) {
      boolean isFocusableComponent = focusableComponents.contains(e
          .getSource());
      boolean isNonEmptyOpposite = e.getOppositeComponent() != null;
      if (isFocusableComponent
          && isNonEmptyOpposite
          && !SwingUtilities.isDescendingFrom(e
              .getOppositeComponent(), peer)) {
        peer.commitOrRevert();
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getSource() == gridPanel
          && evt.getPropertyName().equals(
              CalendarGridPanel.PROPERTY_NAME_DATE)) {
        Date newValue = (Date) evt.getNewValue();
        try {
          peer.setDate(newValue);
        } catch (PropertyVetoException e) {
          // Ignore. Just cannot happen, because CalendarGridPanel
          // already checked the date against current peer's
          // vetoPolicy.
        }
      }
      if (evt.getSource() == gridPanel
          && evt.getPropertyName().equals(
              CalendarGridPanel.PROPERTY_NAME_NOTIFY_SELECTED_DATE_CLICKED)) {
        peer.fireActionEvent();
      }
      if (evt.getSource() == gridPanel
          && evt.getPropertyName().equals(
              CalendarGridPanel.PROPERTY_NAME_BASE_DATE)) {
        Date newValue = (Date) evt.getNewValue();
        modernPanel.setDate(newValue);
        classicPanel.setDate(newValue);
      }
      if (evt.getSource() == modernPanel
          && evt.getPropertyName().equals(
              ModernCalendarPanel.PROPERTY_NAME_DATE)) {
        Date newValue = (Date) evt.getNewValue();

        gridPanel.setBaseDate(newValue);
        classicPanel.setDate(newValue);
        numberOfWeekPanel.setBaseDate(newValue);

      }
      if (evt.getSource() == classicPanel
          && evt.getPropertyName().equals(
              ModernCalendarPanel.PROPERTY_NAME_DATE)) {
        Date newValue = (Date) evt.getNewValue();

        gridPanel.setBaseDate(newValue);
        modernPanel.setDate(newValue);
        numberOfWeekPanel.setBaseDate(newValue);

      }
      if (evt.getSource() == auxPanel
          && evt.getPropertyName()
              .equals(AuxPanel.PROPERTY_NAME_DATE)) {
        Date date = (Date) evt.getNewValue();

        gridPanel.setDate(date);
        peer.commitEdit();
      }

    }
  }

}

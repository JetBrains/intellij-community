package com.michaelbaranov.microba.calendar;

import com.michaelbaranov.microba.calendar.ui.CalendarPaneUI;
import com.michaelbaranov.microba.common.CommitEvent;
import com.michaelbaranov.microba.common.CommitListener;
import com.michaelbaranov.microba.common.MicrobaComponent;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A concrete implementation of JComponent. Capable of displaying and selecting
 * dates, much like a real-world calendar.
 * 
 * @author Michael Baranov
 */
public class CalendarPane extends MicrobaComponent implements
    CalendarColors {

  /**
   * The name of a "date" property.
   */
  public static final String PROPERTY_NAME_DATE = "date";

  /**
   * The name of a "locale" property.
   */
  public static final String PROPERTY_NAME_LOCALE = "locale";

  /**
   * The name of a "zone" property.
   */
  public static final String PROPERTY_NAME_ZONE = "zone";

  /**
   * The name of a "style" property.
   */
  public static final String PROPERTY_NAME_STYLE = "style";

  /**
   * The name of a "showTodayButton" property.
   */
  public static final String PROPERTY_NAME_SHOW_TODAY_BTN = "showTodayButton";

  /**
   * The name of a "showNoneButton" property.
   */
  public static final String PROPERTY_NAME_SHOW_NONE_BTN = "showNoneButton";

  /**
   * The name of a "focusLocatBehavior" property.
   */
  public static final String PROPERTY_NAME_FOCUS_LOST_BEHAVIOR = "focusLostBehavior";

  /**
   * The name of a "vetoPolicy" property.
   */
  public static final String PROPERTY_NAME_VETO_POLICY = "vetoPlicy";

  /**
   * The name of a "holidayPolicy" property.
   */
  public static final String PROPERTY_NAME_HOLIDAY_POLICY = "holidayPolicy";

  /**
   * The name of a "resources" property.
   */
  public static final String PROPERTY_NAME_RESOURCES = "resources";

  /**
   * The name of a "resources" property.
   */
  public static final String PROPERTY_NAME_SHOW_NUMBER_WEEK = "showNumberOfWeek";

  /**
   * The name of a "stripTime" property.
   */
  public static final String PROPERTY_NAME_STRIP_TIME = "stripTime";

  /**
   * A constant for the "style" property.
   */
  public static final int STYLE_MODERN = 0x10;

  /**
   * A constant for the "style" property.
   */
  public static final int STYLE_CLASSIC = 0x20;

  private static final String uiClassID = "microba.CalendarPaneUI";

  private final EventListenerList commitListenerList = new EventListenerList();

  private final EventListenerList actionListenerList = new EventListenerList();

  private Date date;

  private TimeZone zone;

  private Locale locale;

  private VetoPolicy vetoPolicy;

  private HolidayPolicy holidayPolicy;

  private CalendarResources resources;

  private int style;

  private boolean showTodayButton;

  private boolean showNoneButton;

  private int focusLostBehavior;

  private boolean showNumberOfWeek;

  private boolean stripTime;

  @Override
  public String getUIClassID() {
    return uiClassID;
  }

  /**
   * Constructor.
   */
  public CalendarPane() {
    this(null, 0, Locale.getDefault(), TimeZone.getDefault());
  }

  /**
   * Constructor.
   */
  public CalendarPane(int style) {
    this(null, style, Locale.getDefault(), TimeZone.getDefault());
  }

  /**
   * Constructor.
   */
  public CalendarPane(Date initialDate) {
    this(initialDate, 0, Locale.getDefault(), TimeZone.getDefault());
  }

  /**
   * Constructor.
   */
  public CalendarPane(Date initialDate, int style) {
    this(initialDate, style, Locale.getDefault(), TimeZone.getDefault());
  }

  /**
   * Constructor.
   */
  public CalendarPane(Date initialDate, int style, Locale locale) {
    this(initialDate, style, locale, TimeZone.getDefault());
  }

  /**
   * Constructor.
   */
  public CalendarPane(Date initialDate, int style, Locale locale,
      TimeZone zone) {
    checkStyle(style);
    checkLocale(locale);
    checkTimeZone(zone);
    this.style = style;
    this.date = initialDate;
    this.locale = locale;
    this.zone = zone;
    this.focusLostBehavior = JFormattedTextField.COMMIT_OR_REVERT;
    this.showTodayButton = true;
    this.showNoneButton = true;
    this.vetoPolicy = null;
    this.resources = new DefaultCalendarResources();
    this.stripTime = true;

    // forward date property change to action event
    addPropertyChangeListener(PROPERTY_NAME_DATE,
        new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            fireActionEvent();
          }
        });

    updateUI();
  }

  /**
   * Returns currently selected date in the control.
   * <p>
   * The returned date is guaranteed to pass the restriction check by the
   * current {@link VetoPolicy}. Based on the value of {@link #stripTime}
   * property, the returned date may be automatically stripped.
   * 
   * @return currently selected date
   * @see #stripTime
   * @see #stripTime(Date, TimeZone, Locale)
   */
  public Date getDate() {
    if (this.stripTime)
      return stripTime(date, getZone(), getLocale());
    else
      return date;
  }

  /**
   * Sets currently selected date to the control.
   * <p>
   * The given date is checked against the current {@link VetoPolicy}. If the
   * check is passed, the date is transferred to the control and the control
   * is updated to display the date.
   * <p>
   * A {@link PropertyChangeEvent} may be fired, an {@link ActionEvent} may be
   * fired.
   * 
   * @param date
   *            the date to set
   * @throws PropertyVetoException
   *             if the date is restricted by the current {@link VetoPolicy}.
   * @see #getVetoPolicy()
   * @see #setVetoPolicy(VetoPolicy)
   * @see #addActionListener(ActionListener)
   */
  public void setDate(Date date) throws PropertyVetoException {
    if (!checkDate(date)) {
      PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(
          this, PROPERTY_NAME_DATE, this.date, date);
      throw new PropertyVetoException(
          "Value vetoed by current vetoPolicy", propertyChangeEvent);
    }
    Date old = this.date;
    this.date = date;
    if (old != null || date != null) {
      firePropertyChange(PROPERTY_NAME_DATE, old, date);
    }
  }

  /**
   * Returns current locale.
   * 
   * @return current locale
   */
  @Override
  public Locale getLocale() {
    return locale;
  }

  /**
   * Sets current locale.
   * <p>
   * The locale is used to construct internal {@link Calendar} instances and
   * affects visual representation of the control.
   * 
   * @param locale
   *            the locale to set
   */
  @Override
  public void setLocale(Locale locale) {
    Locale old = getLocale();
    this.locale = locale;
    firePropertyChange(PROPERTY_NAME_LOCALE, old, getLocale());
  }

  /**
   * Returns current time zone.
   * 
   * @return current time zone
   */
  public TimeZone getZone() {
    return zone;
  }

  /**
   * Sets current time zone.
   * <p>
   * The time zone is used to construct internal {@link Calendar} instances
   * and affects visual representation of the control. The dates returned by
   * {@link #getDate() } will have all time components set to zero considering
   * the current locale.
   * 
   * @param zone
   *            the time zone to set
   */
  public void setZone(TimeZone zone) {
    TimeZone old = getZone();
    this.zone = zone;
    firePropertyChange(PROPERTY_NAME_ZONE, old, getZone());
  }

  /**
   * Returns current visual style of the control.
   * 
   * @return current visual style constant.
   */
  public int getStyle() {
    return style;
  }

  /**
   * Sets the current visual style of the control.
   * <p>
   * The control is then updated to reflect the new style.
   * 
   * @param style
   *            the style to set
   * @see #STYLE_CLASSIC
   * @see #STYLE_MODERN
   */
  public void setStyle(int style) {
    style = checkStyle(style);
    int old = this.style;
    this.style = style;
    firePropertyChange(PROPERTY_NAME_STYLE, old, style);
  }

  /**
   * Is today button visible?
   * <p>
   * The today button allows the user to quickly select current date.
   * 
   * @return <code>true</code> if the today button is visible,
   *         <code>false</code> otherwise
   */
  public boolean isShowTodayButton() {
    return showTodayButton;
  }

  /**
   * Shows or hides the today-button.
   * <p>
   * The today-button allows the user to quickly select current date.
   * 
   * @param visible
   *            <code>true</code> to show the today-button
   *            <code>false</code> to hide
   */
  public void setShowTodayButton(boolean visible) {
    Boolean old = this.showTodayButton;
    this.showTodayButton = visible;
    firePropertyChange(PROPERTY_NAME_SHOW_TODAY_BTN, old, (Boolean) visible);
  }

  /**
   * Is the none-button visible?
   * <p>
   * The none-button allows the user to select empty date (null-date, no
   * date).
   * 
   * @return <code>true</code> if the none-button is visible,
   *         <code>false</code> otherwise
   */
  public boolean isShowNoneButton() {
    return showNoneButton;
  }

  /**
   * Shows or hides the none-button.
   * <p>
   * The none-button allows the user to select empty date (null-date, no
   * date).
   * 
   * @param visible
   *            <code>true</code> to show the none-button <code>false</code>
   *            to hide
   */
  public void setShowNoneButton(boolean visible) {
    Boolean old = this.showNoneButton;
    this.showNoneButton = visible;
    firePropertyChange(PROPERTY_NAME_SHOW_NONE_BTN, old, (Boolean)visible);
  }

  /**
   * Returns the focus lost behavior. Possible values are:
   * 
   * <ul>
   * <li><code> {@link JFormattedTextField#COMMIT}</code>
   * <li><code> {@link JFormattedTextField#COMMIT_OR_REVERT}</code>
   * <li><code> {@link JFormattedTextField#REVERT}</code>
   * <li><code> {@link JFormattedTextField#PERSIST}</code>
   * </ul>
   * Original meaning preserved.
   * 
   * @return the focus lost behavior constant
   * @see JFormattedTextField
   */
  public int getFocusLostBehavior() {
    return focusLostBehavior;
  }

  /**
   * Sets the focus lost behaviour. Possible values are:
   * 
   * <ul>
   * <li><code> {@link JFormattedTextField#COMMIT}</code>
   * <li><code> {@link JFormattedTextField#COMMIT_OR_REVERT}</code>
   * <li><code> {@link JFormattedTextField#REVERT}</code>
   * <li><code> {@link JFormattedTextField#PERSIST}</code>
   * </ul>
   * Original meaning preserved.
   * 
   * @param behavior
   *            the focus lost behavior constant
   * @see JFormattedTextField
   */
  public void setFocusLostBehavior(int behavior) {
    checkFocusLostBehavior(behavior);
    int old = this.focusLostBehavior;
    this.focusLostBehavior = behavior;
    firePropertyChange(PROPERTY_NAME_FOCUS_LOST_BEHAVIOR, old, behavior);
  }

  /**
   * Returns current calendar resources model.
   * <p>
   * The model is used to query localized resources for the control.
   * 
   * @return current calendar resources model
   * @see CalendarResources
   */
  public CalendarResources getResources() {
    return resources;
  }

  /**
   * Sets current calendar resources model.
   * <p>
   * The model is used to query localized resources for the control.
   * 
   * @param resources
   *            a calendar resources model to set. Should not be
   *            <code>null</code>
   * @see CalendarResources
   */
  public void setResources(CalendarResources resources) {
    CalendarResources old = this.resources;
    this.resources = resources;
    firePropertyChange(PROPERTY_NAME_RESOURCES, old, resources);
  }

  /**
   * Returns current holiday policy (model).
   * <p>
   * The policy is used to query holiday dates and holiday descriptions.
   * 
   * @return current holiday policy or <code>null</code> if none set
   * @see HolidayPolicy
   */
  public HolidayPolicy getHolidayPolicy() {
    return holidayPolicy;
  }

  /**
   * Sets current holiday policy (model) then updates the control to reflect
   * the policy set.
   * <p>
   * The policy is used to query holiday dates and holiday descriptions.
   * 
   * @param holidayPolicy
   *            a holiday policy to set. May be <code>null</code>
   * @see VetoPolicy
   */
  public void setHolidayPolicy(HolidayPolicy holidayPolicy) {
    HolidayPolicy old = this.holidayPolicy;
    this.holidayPolicy = holidayPolicy;
    firePropertyChange(PROPERTY_NAME_HOLIDAY_POLICY, old, holidayPolicy);
  }

  /**
   * Returns the current veto policy (model).
   * <p>
   * The policy is used to veto dates in the control.
   * 
   * @return current veto policy or <code>null</code> if none set
   * @see VetoPolicy
   */
  public VetoPolicy getVetoPolicy() {
    return vetoPolicy;
  }

  /**
   * Sets the current veto policy (model).
   * <p>
   * The policy is used to veto dates in the control.
   * 
   * @param vetoModel
   *            a veto policy to set. May be <code>null</code>
   */
  public void setVetoPolicy(VetoPolicy vetoModel) {
    VetoPolicy old = this.vetoPolicy;
    this.vetoPolicy = vetoModel;
    firePropertyChange(PROPERTY_NAME_VETO_POLICY, old, vetoModel);
  }

  /**
   * Is the number of every week visible?
   * 
   * @return <code>true</code> if the number of every week is visible,
   *         <code>false</code> otherwise
   */
  public boolean isShowNumberOfWeek() {
    return showNumberOfWeek;
  }

  /**
   * Is time protion of the date automatically striped, based on current
   * locale and ime zone?
   * 
   * @return <code>true</code> if {@link #getDate()} returns a stripped
   *         date, <code>false</code> otherwise
   * @see #setStripTime(boolean)
   * @see #stripTime(Date, TimeZone, Locale)
   */
  public boolean isStripTime() {
    return stripTime;
  }

  /**
   * Makes {@link #getDate()} either strip the time portion of the date, or
   * keep it.
   * 
   * @param stripTime
   *            <code>true</code> to strip time, <code>false</code> to
   *            keep time
   */
  public void setStripTime(boolean stripTime) {
    this.stripTime = stripTime;
  }

  /**
   * Shows or hides the number of every week.
   * <p>
   * The number of week is based on the current locale for the component.
   * 
   * @param visible
   *            <code>true</code> to show the number of every week
   *            <code>false</code> to hide
   */
  public void setShowNumberOfWeek(boolean visible) {
    boolean old = this.showNumberOfWeek;
    this.showNumberOfWeek = visible;
    firePropertyChange(PROPERTY_NAME_SHOW_NUMBER_WEEK, old, visible);
  }

  /**
   * Adds an {@link ActionListener} listener.
   * 
   * @param listener
   *            a listener to add
   * @see ActionListener
   */
  public void addActionListener(ActionListener listener) {
    actionListenerList.add(ActionListener.class, listener);
  }

  /**
   * Removes an {@link ActionListener} listener.
   * 
   * @param listener
   *            a listener to remove
   * @see ActionListener
   */
  public void removeActionListener(ActionListener listener) {
    actionListenerList.remove(ActionListener.class, listener);

  }

  /**
   * Adds an {@link CommitListener} listener.
   * 
   * @param listener
   *            a listener to add
   * @see CommitListener
   */
  public void addCommitListener(CommitListener listener) {
    commitListenerList.add(CommitListener.class, listener);
  }

  /**
   * Removes an {@link CommitListener} listener.
   * 
   * @param listener
   *            a listener to remove
   * @see CommitListener
   */
  public void removeCommitListener(CommitListener listener) {
    commitListenerList.remove(CommitListener.class, listener);
  }

  /**
   * Forces the control to commit current user's edit. The operation may fail
   * because the date in the control may be restricted by current veto policy.
   * If successful, the current date of the control may change, a
   * {@link CommitEvent} is fired.
   * 
   * @return <code>true</code> if successful, <code>false</code> otherwise
   * @see #revertEdit()
   * @see #getFocusLostBehavior()
   * @see #setFocusLostBehavior(int)
   */
  public boolean commitEdit() {
    try {
      ((CalendarPaneUI) getUI()).commit();
      fireCommitEvent(true);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Forces the control to revert current user's edit to reflect current
   * control's date. The current date of the control may change, a
   * {@link CommitEvent} is fired.
   * 
   * @see #revertEdit()
   * @see #getFocusLostBehavior()
   * @see #setFocusLostBehavior(int)
   */
  public void revertEdit() {
    ((CalendarPaneUI) getUI()).revert();
    fireCommitEvent(false);
  }

  /**
   * Forces the control to commit or revert user's edit depending on the
   * current focus lost behavior as if the focus would be lost.
   * 
   * @see #commitEdit()
   * @see #revertEdit()
   * @see #getFocusLostBehavior()
   * @see #setFocusLostBehavior(int)
   */
  public void commitOrRevert() {
    switch (focusLostBehavior) {
    case JFormattedTextField.REVERT:
      revertEdit();
      break;
    case JFormattedTextField.COMMIT:
      commitEdit();
      break;
    case JFormattedTextField.COMMIT_OR_REVERT:
      if (!commitEdit())
        revertEdit();
      break;
    case JFormattedTextField.PERSIST:
      // do nothing
      break;
    }
  }

  /**
   * Fires a {@link CommitEvent} to all registered listeners.
   * 
   * @param commit
   *            <code>true</code> to indicate commit, <code>false</code>
   *            to indicate revert
   * @see CommitEvent
   * @see CommitListener
   */
  public void fireCommitEvent(boolean commit) {
    Object[] listeners = commitListenerList.getListenerList();

    for (int i = listeners.length - 2; i >= 0; i -= 2)
      if (listeners[i] == CommitListener.class)
        ((CommitListener) listeners[i + 1]).commit(new CommitEvent(
            this, commit));
  }

  /**
   * Fires a {@link ActionEvent} to all registered listeners.
   * 
   * @see ActionEvent
   * @see ActionListener
   */
  public void fireActionEvent() {
    Object[] listeners = actionListenerList.getListenerList();

    for (int i = listeners.length - 2; i >= 0; i -= 2)
      if (listeners[i] == ActionListener.class)
        ((ActionListener) listeners[i + 1])
            .actionPerformed(new ActionEvent(this, 0, "value"));
  }

  private static void checkTimeZone(TimeZone zone) {
    if (zone == null)
      throw new IllegalArgumentException("'zone' can not be null.");

  }

  private static void checkLocale(Locale locale) {
    if (locale == null)
      throw new IllegalArgumentException("'locale' can not be null.");

  }

  private static void checkFocusLostBehavior(int behavior) {
    if (behavior != JFormattedTextField.COMMIT
        && behavior != JFormattedTextField.COMMIT_OR_REVERT
        && behavior != JFormattedTextField.REVERT
        && behavior != JFormattedTextField.PERSIST)
      throw new IllegalArgumentException(
          PROPERTY_NAME_FOCUS_LOST_BEHAVIOR
              + ": unrecognized behavior");
  }

  private boolean checkDate(Date date) {
    if (vetoPolicy != null) {
      if (date == null)
        return !vetoPolicy.isRestrictNull(this);
      return !vetoPolicy.isRestricted(this, makeCurrentCalendar(date));
    } else
      return true;
  }

  private static int checkStyle(int style) {
    if (style == 0)
      style = STYLE_CLASSIC;
    if (style != STYLE_CLASSIC && style != STYLE_MODERN)
      throw new IllegalArgumentException(PROPERTY_NAME_STYLE
          + ": unrecognized style");
    return style;
  }

  private Calendar makeCurrentCalendar(Date date) {
    Calendar c = Calendar.getInstance(zone, locale);
    c.setTime(date);
    return c;
  }

  /**
   * Returns the same date as given, but time portion (hours, minutes, seconds,
   * fraction of second) set to zero, based on given locale and time zone.
   * Utility method.
   * <p>
   * Example:<br>
   * Fri Sep 29 15:57:23 EEST 2006 -> Fri Sep 29 00:00:00 EEST 2006
   * 
   * @param date
   *            date to strip time from
   * @param zone
   *            time zone to get zero fields in
   * @param locale
   *            locale to base the calendar on
   * @return stripped date
   */
  public static Date stripTime(Date date, TimeZone zone, Locale locale) {
    if (date == null)
      return null;
    Calendar tmpCalendar = Calendar.getInstance(zone, locale);
    tmpCalendar.setTime(date);
    tmpCalendar.set(Calendar.HOUR_OF_DAY, tmpCalendar
        .getMinimum(Calendar.HOUR_OF_DAY));
    tmpCalendar.set(Calendar.MINUTE, tmpCalendar
        .getMinimum(Calendar.MINUTE));
    tmpCalendar.set(Calendar.SECOND, tmpCalendar
        .getMinimum(Calendar.SECOND));
    tmpCalendar.set(Calendar.MILLISECOND, tmpCalendar
        .getMinimum(Calendar.MILLISECOND));
    return tmpCalendar.getTime();
  }

}

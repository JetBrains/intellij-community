package com.michaelbaranov.microba.calendar;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.michaelbaranov.microba.calendar.ui.DatePickerUI;

/**
 * A concrete implementation of JComponent. Capable of displaying and selecting
 * dates, much like an editable combo-box with a calendar dropdown.
 * <p>
 * This implementatin allows for specifying time component along with the date.
 * Make sure that: 1) keepTime property is true; 2) stripTime is false; 3)
 * dateFormat has time fields;
 * 
 * @author Michael Baranov
 */
public class DatePicker extends CalendarPane {

	/**
	 * The name of a "dateFormat" property.
	 */
	public static final String PROPERTY_NAME_DATE_FORMAT = "dateFormat";

	/**
	 * The name of a "fieldEditable" property.
	 */
	public static final String PROPERTY_NAME_FIELD_EDITABLE = "fieldEditable";

	/**
	 * The name of a "keepTime" property.
	 */
	public static final String PROPERTY_NAME_KEEP_TIME = "keepTime";

	/**
	 * The name of a "pickerStyle" property.
	 */
	public static final String PROPERTY_NAME_PICKER_STYLE = "pickerStyle";

	/**
	 * The name of a "popupFocusable" property.
	 */
	public static final String PROPERTY_NAME_DROPDOWN_FOCUSABLE = "dropdownFocusable";

	/**
	 * A constant for the "pickerStyle" property.
	 */
	public static final int PICKER_STYLE_FIELD_AND_BUTTON = 0x110;

	/**
	 * A constant for the "pickerStyle" property.
	 */
	public static final int PICKER_STYLE_BUTTON = 0x120;

	private static final String uiClassID = "microba.DatePickerUI";

	private DateFormat dateFormat;

	private boolean fieldEditable;

	private boolean keepTime;

	private int pickerStyle;

	private boolean dropdownFocusable;

	/**
	 * Constructor.
	 */
	public DatePicker() {
		this(new Date(), DateFormat.MEDIUM, Locale.getDefault(), TimeZone
				.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate) {
		this(initialDate, DateFormat.MEDIUM, Locale.getDefault(), TimeZone
				.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, int dateStyle) {
		this(initialDate, dateStyle, Locale.getDefault(), TimeZone.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, DateFormat dateFormat) {
		this(initialDate, dateFormat, Locale.getDefault(), TimeZone
				.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, int dateStyle, Locale locale) {
		this(initialDate, dateStyle, locale, TimeZone.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, DateFormat dateFormat, Locale locale) {
		this(initialDate, dateFormat, locale, TimeZone.getDefault());
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, int dateStyle, Locale locale,
			TimeZone zone) {
		super(initialDate, CalendarPane.STYLE_CLASSIC, locale, zone);
		checkDateStyle(dateStyle);
		this.dateFormat = dateFormatFromStyle(dateStyle);
		this.fieldEditable = true;
		this.keepTime = true;
		this.pickerStyle = PICKER_STYLE_FIELD_AND_BUTTON;
		this.setStripTime(false);
		this.dropdownFocusable = true;
		updateUI();
	}

	/**
	 * Constructor.
	 */
	public DatePicker(Date initialDate, DateFormat dateFormat, Locale locale,
			TimeZone zone) {
		super(initialDate, CalendarPane.STYLE_CLASSIC, locale, zone);
		checkDateFormat(dateFormat);
		this.dateFormat = dateFormat;
		this.fieldEditable = true;
		this.keepTime = true;
		this.pickerStyle = PICKER_STYLE_FIELD_AND_BUTTON;
		this.setStripTime(false);
		this.dropdownFocusable = true;
		updateUI();
	}

	public String getUIClassID() {
		return uiClassID;
	}

	/**
	 * Returns the date format.
	 * <p>
	 * 
	 * @return current date format
	 * @see #setDateFormat(DateFormat)
	 */
	public DateFormat getDateFormat() {
		return dateFormat;
	}

	/**
	 * Sets the date format constant defined by {@link DateFormat} and updates
	 * the control to reflect new date style.
	 * <p>
	 * 
	 * @param dateFormat
	 *            the date format constant to set
	 * @see #getDateFormat()
	 * @see DateFormat
	 */
	public void setDateFormat(DateFormat dateFormat) {
		checkDateFormat(dateFormat);
		Object oldValue = this.dateFormat;
		this.dateFormat = dateFormat;
		firePropertyChange(PROPERTY_NAME_DATE_FORMAT, oldValue, dateFormat);
	}

	/**
	 * Is the edit field of the control editable by the user?
	 * <p>
	 * If not editable, the user can not type in the date and can only use
	 * calendar drop-down to select dates.
	 * 
	 * @return <code>true</code> if the edit field is editable,
	 *         <code>false</code> otherwise
	 * 
	 * @see #setFieldEditable(boolean)
	 */
	public boolean isFieldEditable() {
		return fieldEditable;
	}

	/**
	 * Enables or disables editing of the edit field by the user.
	 * <p>
	 * If not editable, the user can not type in the date and can only use
	 * calendar drop-down to select dates.
	 * 
	 * @param fieldEditable
	 *            the editable value to set
	 * 
	 * @see #isFieldEditable()
	 */
	public void setFieldEditable(boolean fieldEditable) {
		boolean old = this.fieldEditable;
		this.fieldEditable = fieldEditable;
		firePropertyChange(PROPERTY_NAME_FIELD_EDITABLE, old, fieldEditable);
	}

	/**
	 * Is the dropdown focusable?
	 * <p>
	 * If not focusable, the dropdown calendar will lack some keyboard input
	 * capabilities.
	 * 
	 * @return <code>true</code> if the dropdown is focusable,
	 *         <code>false</code> otherwise
	 * 
	 * @see #setDropdownFocusable(boolean)
	 */
	public boolean isDropdownFocusable() {
		return dropdownFocusable;
	}

	/**
	 * Enables or disables focusability of the dropdown calendar.
	 * <p>
	 * If not focusable, the dropdown calendar will lack some keyboard input
	 * capabilities.
	 * 
	 * @param popupFocusable
	 *            the focusable value to set
	 * 
	 * @see #isDropdownFocusable()
	 */
	public void setDropdownFocusable(boolean popupFocusable) {
		boolean old = this.dropdownFocusable;
		this.dropdownFocusable = popupFocusable;
		firePropertyChange(PROPERTY_NAME_DROPDOWN_FOCUSABLE, old,
				popupFocusable);
	}

	/**
	 * Does UI try to preserve time components entered in the edit field?
	 * <p>
	 * If <code>true</code> and if the date format has some time fields
	 * (hours, minutes, seconds, fraction of second), the UI tries to respect
	 * the time fields' values entered by user as much as possible.
	 * <p>
	 * Note: to be able to receive time portion of the date, make sure
	 * {@link #isStripTime()} is <code>false</code> (the dafualt).
	 * 
	 * @return <code>true</code> if the UI respects time fields,
	 *         <code>false</code> otherwise
	 * @see #setKeepTime(boolean)
	 * @see #setStripTime(boolean)
	 * @see #isStripTime()
	 * 
	 */
	public boolean isKeepTime() {
		return keepTime;
	}

	/**
	 * Determines if the UI should try to preserve time components entered in
	 * the edit field.
	 * <p>
	 * If <code>true</code> and if the date format has some time fields
	 * (hours, minutes, seconds, fraction of second), the UI tries to respect
	 * the time fields' values entered by user as much as possible.
	 * <p>
	 * Note: to be able to receive time portion of the date, make sure
	 * {@link #isStripTime()} is <code>false</code> (the dafualt).
	 * 
	 * @param keepTime
	 *            <code>true</code> to make the UI respects time fields,
	 *            <code>false</code> otherwise
	 * @see #isKeepTime()
	 * @see #setStripTime(boolean)
	 * @see #isStripTime()
	 */
	public void setKeepTime(boolean keepTime) {
		boolean old = this.keepTime;
		this.keepTime = keepTime;
		firePropertyChange(PROPERTY_NAME_KEEP_TIME, old, keepTime);
	}

	/**
	 * Returns current visual style of the picker control.
	 * <p>
	 * NOTE: do not confuse with {@link #getStyle()}.
	 * 
	 * @return current visual style constant.
	 */
	public int getPickerStyle() {
		return pickerStyle;
	}

	/**
	 * Sets the current visual style of the picker control.
	 * <p>
	 * The control is then updated to reflect the new style.
	 * <p>
	 * NOTE: do not confuse with {@link #getStyle()}.
	 * 
	 * @param pickerStyle
	 *            the style to set
	 * @see #PICKER_STYLE_BUTTON
	 * @see #PICKER_STYLE_FIELD_AND_BUTTON
	 */
	public void setPickerStyle(int pickerStyle) {
		pickerStyle = checkPickerStyle(pickerStyle);
		int oldValue = this.pickerStyle;
		this.pickerStyle = pickerStyle;
		firePropertyChange(PROPERTY_NAME_PICKER_STYLE, oldValue, pickerStyle);
	}

	/**
	 * A shortucut method to switch picker style between
	 * {@link #PICKER_STYLE_FIELD_AND_BUTTON} and {@link #PICKER_STYLE_BUTTON}
	 * 
	 * @param buttonOnly
	 *            <code>true</code> to set {@link #PICKER_STYLE_BUTTON},
	 *            <code>false</code> to set
	 *            {@link #PICKER_STYLE_FIELD_AND_BUTTON}
	 */
	public void showButtonOnly(boolean buttonOnly) {
		if (buttonOnly)
			setPickerStyle(PICKER_STYLE_BUTTON);
		else
			setPickerStyle(PICKER_STYLE_FIELD_AND_BUTTON);
	}

	/**
	 * Displays the calendar dropdown.
	 */
	public void showPopup() {
		((DatePickerUI) getUI()).showPopup(true);
	}

	/**
	 * Hides the calendar dropdown without selecting a date.
	 */
	public void hidePopup() {
		((DatePickerUI) getUI()).showPopup(false);
	}

	public boolean commitEdit() {
		try {
			((DatePickerUI) getUI()).commit();
			fireCommitEvent(true);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void revertEdit() {
		((DatePickerUI) getUI()).revert();
		fireCommitEvent(false);
	}

	private void checkDateStyle(int style) {
		if (style != DateFormat.SHORT && style != DateFormat.MEDIUM
				&& style != DateFormat.LONG)
			throw new IllegalArgumentException("dateStyle: unrecognized style");
	}

	private int checkPickerStyle(int style) {
		if (style == 0)
			style = PICKER_STYLE_FIELD_AND_BUTTON;
		if (style != PICKER_STYLE_FIELD_AND_BUTTON
				&& style != PICKER_STYLE_BUTTON)
			throw new IllegalArgumentException(PROPERTY_NAME_PICKER_STYLE
					+ ": unrecognized style");
		return style;
	}

	private void checkDateFormat(DateFormat dateFormat) {
		if (dateFormat == null)
			throw new IllegalArgumentException("dateFormat: null value");
	}

	private DateFormat dateFormatFromStyle(int dateStyle) {
		DateFormat df = DateFormat.getDateInstance(dateStyle, this.getLocale());
		df.setTimeZone(this.getZone());
		return df;
	}

}

package com.michaelbaranov.microba.calendar;

import java.util.Calendar;

import com.michaelbaranov.microba.common.Policy;

/**
 * This interface is used by {@link CalendarPane} and {@link DatePicker} to
 * provide means to define hollidays and optionally provide descriptions to
 * them.
 * 
 * @author Michael Baranov
 * 
 */
public interface HolidayPolicy extends Policy {
	/**
	 * This method is used to check if a date is a holliday. Holliday dates are
	 * displayed by contols in a special way.
	 * 
	 * @param source
	 *            a control calling this method
	 * @param date
	 *            a date to check
	 * @return <code>true</code> if given <code>date</code> is a holliday
	 *         <code>false</code> otherwise
	 */
	public boolean isHolliday(Object source, Calendar date);

	/**
	 * This method is used to check if a date is a weekend date. Implementation
	 * should only check day of week component of the date.
	 * 
	 * @param source
	 *            a control calling this method
	 * @param date
	 *            a date to check
	 * @return <code>true</code> if given <code>date</code> is weekend date
	 *         <code>false</code> otherwise
	 */
	public boolean isWeekend(Object source, Calendar date);

	/**
	 * This method is used to query a description for a holliday date. Note,
	 * that implementation may return localized results based on the locale
	 * passed with the date.
	 * 
	 * @param source
	 *            a control calling this method
	 * @param date
	 *            a holliday date to get the description for
	 * @return a localized name (a description) for a holliday
	 */
	public String getHollidayName(Object source, Calendar date);

}

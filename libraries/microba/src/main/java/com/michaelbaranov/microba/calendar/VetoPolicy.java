package com.michaelbaranov.microba.calendar;

import java.util.Calendar;

import com.michaelbaranov.microba.common.Policy;

/**
 * This interface is used by {@link CalendarPane} and {@link DatePicker} to
 * provide means to restrict dates in a control.
 * 
 * @author Michael Baranov
 * 
 */
public interface VetoPolicy extends Policy {

	/**
	 * This method is used to check if a date is restricted. Restricted dates
	 * can not be selected by users in a control.
	 * 
	 * @param source
	 *            a control calling this method
	 * @param date
	 *            a date to check. Is never <code>null</code>
	 * @return <code>true</code> if given <code>date</code> is restricted
	 *         <code>false</code> otherwise
	 */
	public boolean isRestricted(Object source, Calendar date);

	/**
	 * This method is used to check if no-date (<code>null</code> date) is
	 * restricted. Restricted dates can not be selected by users in a control.
	 * 
	 * @param source
	 *            a control calling this method
	 * @return <code>false</code> to allow no-date, <code>true</code>
	 *         otherwise
	 */
	public boolean isRestrictNull(Object source);

}

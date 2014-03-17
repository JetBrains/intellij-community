package com.michaelbaranov.microba.calendar;

import java.util.Locale;

/**
 * An interface is used to provide localized string resources for
 * {@link CalendarPane} and {@link DatePicker} classes.
 * 
 * @author Michael Baranov
 * 
 */
public interface CalendarResources {

	/**
	 * A key for "today" word
	 */
	public static final String KEY_TODAY = "key.today";

	/**
	 * A key for "none" word
	 */
	public static final String KEY_NONE = "key.none";

	/**
	 * This method is used to query tring resources for {@link CalendarPane} and
	 * {@link DatePicker} classes. Should not return <code>null</code>.
	 * 
	 * @param key
	 *            one of the keys defined by {@link CalendarResources}
	 * @param locale
	 *            a {@link Locale}
	 * @return localized string resource for a given key
	 */
	public String getResource(String key, Locale locale);
}

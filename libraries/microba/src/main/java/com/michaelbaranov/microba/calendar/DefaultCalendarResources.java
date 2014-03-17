package com.michaelbaranov.microba.calendar;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/**
 * A very basic implementation of {@link CalendarResources}. Used by default by
 * {@link CalendarPane} and {@link DatePicker} classes. The resources are loaded
 * from 'DefaultCalendarResources.properties' file.
 * 
 * @author Michael Baranov
 * 
 */
public class DefaultCalendarResources implements CalendarResources {

	private static final String RESOURCE_FILE = "DefaultCalendarResources.properties";

	private static final String DEFAULT_LANGUAGE = "en";

	private Properties strings = new Properties();

	/**
	 * Constructor.
	 */
	public DefaultCalendarResources() {
		try {
			strings.load(DefaultCalendarResources.class
					.getResourceAsStream(RESOURCE_FILE));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getResource(String key, Locale locale) {
		String language = locale.getLanguage();
		String word = (String) strings.get(language + "." + key);
		if (word == null)
			word = (String) strings.get(DEFAULT_LANGUAGE + "." + key);
		return word;
	}

}

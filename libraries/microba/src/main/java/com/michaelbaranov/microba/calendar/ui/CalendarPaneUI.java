package com.michaelbaranov.microba.calendar.ui;

import java.beans.PropertyVetoException;
import java.text.ParseException;

import javax.swing.plaf.ComponentUI;

public abstract class CalendarPaneUI extends ComponentUI {

	public abstract void commit() throws PropertyVetoException, ParseException;

	public abstract void revert();

}

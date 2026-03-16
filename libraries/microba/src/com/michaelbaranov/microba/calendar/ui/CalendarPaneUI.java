package com.michaelbaranov.microba.calendar.ui;

import javax.swing.plaf.ComponentUI;
import java.beans.PropertyVetoException;
import java.text.ParseException;

public abstract class CalendarPaneUI extends ComponentUI {

  public abstract void commit() throws PropertyVetoException, ParseException;

  public abstract void revert();

}

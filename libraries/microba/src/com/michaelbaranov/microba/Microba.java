package com.michaelbaranov.microba;

import com.michaelbaranov.microba.calendar.ui.basic.BasicCalendarPaneUI;
import com.michaelbaranov.microba.calendar.ui.basic.BasicDatePickerUI;
import com.michaelbaranov.microba.common.MicrobaComponent;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to initialize Microba library.
 * 
 * @author Michael Baranov
 * 
 */
public final class Microba {

  private static final UIChangeListener changeListener = new UIChangeListener();

  /**
   * Initializes the library: installs L&F properties, sets up a L&F change
   * listener.
   * <p>
   * No need to call this method explicitly for desktop applications. You
   * should only call it in {@link Applet#init()}. This will handle the browser 
   * refresh button correctly.
   * 
   */
  public static synchronized void init() {
    setLookAndFeelProperties(UIManager.getLookAndFeel());

    UIManager.removePropertyChangeListener(changeListener);
    UIManager.addPropertyChangeListener(changeListener);
  }

  private static synchronized void setLookAndFeelProperties(LookAndFeel lookAndFeel) {
    if (lookAndFeel == null) {
      return;
    }

    String packagePrefix = "com.michaelbaranov.microba.";

    // all L&F
    UIManager.put("microba.CalendarPaneUI", packagePrefix + "calendar.ui.basic.BasicCalendarPaneUI");
    UIManager.put(packagePrefix + "calendar.ui.basic.BasicCalendarPaneUI", BasicCalendarPaneUI.class);
    UIManager.put("microba.DatePickerUI", packagePrefix + "calendar.ui.basic.BasicDatePickerUI");
    UIManager.put(packagePrefix + "calendar.ui.basic.BasicDatePickerUI", BasicDatePickerUI.class);
  }

  private static final class UIChangeListener implements
      PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if ("lookAndFeel".equals(event.getPropertyName())) {
        setLookAndFeelProperties((LookAndFeel) event.getNewValue());
      }
    }
  }

  private static final Map<String, Map<String, Color>> lookAndFeelToOverride = new HashMap<>();

  /**
   * Sets per-Look&Feel map of color overrides.
   * 
   * 
   * @param lookAndFeel
   *            look&feel ID
   * @param overrides
   *            keys in the map are {@link String} constants, values are of
   *            type {@link Color} or of type {@link String} (in this case,
   *            {@link Color} values are obtained via
   *            {@link UIManager#getColor(Object)}). May be <code>null</code>.
   */
  public static void setColorOverrideMap(String lookAndFeel, Map<String, Color> overrides) {
    lookAndFeelToOverride.put(lookAndFeel, overrides);
    // TODO: refresh ui delegates
  }

  /**
   * Returns overridden color for a given component in current Look&Feel. The
   * algorithm is:
   * <ul>
   * <li>If the component overrides the constant (per-instance override),
   * then it is returned.
   * <li>If the library overrides the constant (per-Look&Feel override), then
   * it is returned.
   * <li>Else <code>null</code> is returned.
   * </ul>
   * This method is actually intended to be used by UI delegates of the
   * library.
   * 
   * @param colorConstant
   *            color constant
   * @param component
   *            component of the library
   * @return overridden color or <code>null</code> if not overridden
   */
  public static synchronized Color getOverridenColor(String colorConstant,
      MicrobaComponent component) {

    Map<String, Color> componentOverrideMap = component.getColorOverrideMap();
    if (componentOverrideMap != null) {
      if (componentOverrideMap.containsKey(colorConstant)) {
        Color val = componentOverrideMap.get(colorConstant);
        if (val != null)
          return val;
        else
          return UIManager.getColor(val);
      }
    }

    String currentLookAndFeel = UIManager.getLookAndFeel().getID();
    Map<String, Color> overrides = lookAndFeelToOverride.get(currentLookAndFeel);
    if (overrides != null) {
      if (overrides.containsKey(colorConstant)) {
        Color val = overrides.get(colorConstant);
        if (val != null)
          return val;
        else
          return UIManager.getColor(val);

      }
    }

    return null;
  }

  /**
   * Returns overridden color for a given component in current Look&Feel or a
   * default value. The algorithm is:
   * <ul>
   * <li>If the component overrides the constant (per-instance override),
   * then it is returned.
   * <li>If the library overrides the constant (per-Look&Feel override), then
   * it is returned.
   * <li>Else defaultColor is returned.
   * </ul>
   * This method is actually intended to be used by UI delegates of the
   * library.
   * 
   * @param colorConstant
   *            color constant
   * @param component
   *            component of the library
   * @return overridden color or defaultColor if not overridden
   */
  public static synchronized Color getOverridenColor(String colorConstant,
      MicrobaComponent component, Color defaultColor) {
    Color overridden = getOverridenColor(colorConstant, component);
    if (overridden != null)
      return overridden;
    else
      return defaultColor;
  }

}

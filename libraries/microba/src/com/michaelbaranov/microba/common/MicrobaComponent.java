package com.michaelbaranov.microba.common;

import com.michaelbaranov.microba.Microba;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.util.Collections;
import java.util.Map;

/**
 * Superclass for all Microba GUI components.
 * 
 * @author Michael Baranov
 * 
 */
public class MicrobaComponent extends JComponent {

  public static final String PROPERTY_NAME_COLOR_OVERRIDE_MAP = "colorOverrideMap";

  static {
    Microba.init();
  }

  protected Map<String, Color> colorOverrideMap;

  @Override
  public ComponentUI getUI() {
    return ui;
  }

  /**
   * Sets the UI delegate of this component to the corresponding UI delegate
   * taken from UIManager.
   */
  @Override
  public void updateUI() {
    ComponentUI delegate = UIManager.getUI(this);
    setUI(delegate);
  }

  /**
   * Returns per-instance (only for this instance) map of color overrides. May
   * be <code>null</code>.
   * <p>
   * NOTE: returned map is unmodifiable. Use {@link #setColorOverrideMap(Map)}
   * to change the map.
   * 
   * @return keys in the map are {@link String} constants, valuse are of type
   *         {@link Color} or of type {@link String} (in this case,
   *         {@link Color} values are obtained via
   *         {@link UIManager#getColor(Object)})
   */
  public Map<String, Color> getColorOverrideMap() {
    if (colorOverrideMap == null)
      return null;

    return Collections.unmodifiableMap(colorOverrideMap);
  }

  /**
   * Sets per-instance (only for this instance) map of color overrides.
   * 
   * @param colorOverrideMap
   *            keys in the map are {@link String} constants, valuse are of
   *            type {@link Color} or of type {@link String} (in this case,
   *            {@link Color} values are obtained via
   *            {@link UIManager#getColor(Object)}). May be <code>null</code>.
   */
  public void setColorOverrideMap(Map<String, Color> colorOverrideMap) {
    Object old = this.colorOverrideMap;
    this.colorOverrideMap = colorOverrideMap;
    firePropertyChange(PROPERTY_NAME_COLOR_OVERRIDE_MAP, old,
        colorOverrideMap);

    updateUI();
  }

}

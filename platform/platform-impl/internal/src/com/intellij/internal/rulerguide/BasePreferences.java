package com.intellij.internal.rulerguide;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;

import java.awt.*;

final class BasePreferences {

    public static final Color ERROR_COLOR = new JBColor(
            new Color(255, 0, 0, 32),
            new Color(255, 100, 100, 64)
    );
    public static final Color FINE_COLOR = new JBColor(
            new Color(0, 255, 128, 32),
            new Color(98, 150, 85, 64)
    );
    public static final Color BASE_COLOR = JBColor.GREEN;
    public static final Color COMPONENT_COLOR = JBColor.ORANGE;
    public static final Color BACKGROUND_COLOR = new JBColor(
            new Color(0, 0, 0, 32),
            new Color(255, 255, 255, 32)
    );
    
    public static int getAllowedGap() {
        return Registry.intValue("ide.ruler.guide.allowed.gap", 1);
    }

    private BasePreferences() {
    }
}

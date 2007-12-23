package com.intellij.codeInsight.lookup;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.12.2004
 * Time: 13:25:56
 * To change this template use File | Settings | File Templates.
 */
public interface LookupValueWithUIHint extends PresentableLookupValue {
  String getTypeHint();
  Color getColorHint();
  boolean isBold();
}

package com.intellij.lang.parameterInfo;

import java.awt.*;
import java.util.EnumSet;

/**
 * Richer interface for decribing a popup hint contents.
 * User: dcheryasov
 * Date: Sep 6, 2008
 */
public interface ParameterInfoUIContextEx extends ParameterInfoUIContext {

  /**
   * Set the contents and formatting of a popup hint.
   * @param texts pieces ot text to be put together, each individually formattable.
   * @param flags a set of Flags; flags[i] describes formatting of texts[i].
   * @param isDisabledBeforeHighlight disable the hint before highlighting.
   * @param background background color of the hint.
   */
  void setupUIComponentPresentation(String[] texts, EnumSet<Flag>[] flags, boolean isDisabledBeforeHighlight, Color background);

  enum Flag {
    HIGHLIGHT, DISABLE, STRIKEOUT // more to come
  }
}

package org.example.util;

import java.text.MessageFormat;

class Formatter {
  public static String formatNew(String pattern, Object... arguments) {
    return MessageFormat.format(pattern, arguments);
  }

  public static void main(String[] args)
  {
    final String format2 = formatNew("Hello {0}",     "Alice", <warning descr="Argument with index '1' is not used in the pattern">"Bob"</warning>);
  }
}
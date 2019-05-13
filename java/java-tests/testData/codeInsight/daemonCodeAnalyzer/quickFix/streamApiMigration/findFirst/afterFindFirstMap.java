// "Replace with findFirst()" "true"

import java.util.stream.IntStream;

class Lookup {
  boolean matches(char[] lookup) {}
}

// IDEA-200209
class C {
  private Lookup[] lookbehindFormats;
  private char[] lookupChars;
  private Lookup lookbehindFormat;

  {
      IntStream.range(0, lookbehindFormats.length).filter(i -> lookbehindFormats[i].matches(lookupChars)).findFirst().ifPresent(i -> lookbehindFormat = lookbehindFormats[i]);

  }
}
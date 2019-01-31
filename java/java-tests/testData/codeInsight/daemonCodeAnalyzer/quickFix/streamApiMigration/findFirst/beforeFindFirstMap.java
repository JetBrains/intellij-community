// "Replace with findFirst()" "true"

class Lookup {
  boolean matches(char[] lookup) {}
}

// IDEA-200209
class C {
  private Lookup[] lookbehindFormats;
  private char[] lookupChars;
  private Lookup lookbehindFormat;

  {
    for (int i = 0; i < lookbehin<caret>dFormats.length; i++) {
      if (lookbehindFormats[i].matches(lookupChars)) {
        lookbehindFormat = lookbehindFormats[i];
        break;
      }
    }

  }
}
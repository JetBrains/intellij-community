class UnnecessaryUnicodeEscapeUTF8 {

  char replacement = '\uFFFD';

  public void foo() {
    var stroke1 = '\u0336';
    var stroke2 = '̶';

    var hook1 = '\u0309';
    var hook2 = '̉';

    var macron1 = '\ufe24';
    var macron2 = '︤';
  }
}
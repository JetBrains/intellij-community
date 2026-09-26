class ShouldReplaceStripByIsBlank {
  boolean useIsEmpty(String text) {
    return text.<warning descr="'strip()' call can be replaced with 'isBlank()'">strip</warning>().isEmpty();
  }

  boolean useLength(String text) {
    return text.strip().length() == 0;
  }

  boolean useEquals(String text) {
    return text.strip().equals("");
  }
}

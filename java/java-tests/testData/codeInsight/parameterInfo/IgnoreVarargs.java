class Test {
  {
    final String method = getParentOfType(String.class, CharSe<caret>quence.class);
  }

  public static <T extends CharSequence> T getParentOfType(Class<T> a, Class<? extends CharSequence>... stopAt) {
    return null;
  }
}
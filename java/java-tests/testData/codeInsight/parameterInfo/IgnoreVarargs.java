class Test {
  {
    final String method = getParentOfType(String.class, CharSe<caret>quence.class);
  }

  public static <T extends CharSequence> T getParentOfType(Class<T> aClass, Class<? extends CharSequence>... stopAt) {
    return null;
  }
}
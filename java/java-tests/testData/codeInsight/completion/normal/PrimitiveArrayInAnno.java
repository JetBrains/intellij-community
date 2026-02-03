private class Zooooooo {
  @interface B {
      Class<?> value();
  }

  @B(byte[].c<caret>)
  public static void main(String[] args) {
  }
}

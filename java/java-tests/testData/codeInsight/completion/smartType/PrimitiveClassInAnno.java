class Bar {
  @interface B {
      Class<?> value();
  }

  @B(byte[].<caret>)
  public static void main(String[] args) {
  }

}
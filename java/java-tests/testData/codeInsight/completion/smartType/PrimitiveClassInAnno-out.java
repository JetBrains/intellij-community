class Bar {
  @interface B {
      Class<?> value();
  }

  @B(byte[].class<caret>)
  public static void main(String[] args) {
  }

}
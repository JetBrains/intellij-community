// "Change field 'foo' type to 'String[]'" "true-preview"

class Base {
  private String[] foo;
  public void bar(String... args) {
    foo = args;
  }
}
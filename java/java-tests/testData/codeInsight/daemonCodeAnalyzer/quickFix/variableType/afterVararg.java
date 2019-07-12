// "Change field 'foo' type to 'String[]'" "true"

class Base {
  private String[] foo;
  public void bar(String... args) {
    foo = args;
  }
}
// "Change field 'foo' type to 'java.lang.String[]'" "true"

class Base {
  private String foo;
  public void bar(String... args) {
    foo<caret> = args;
  }
}
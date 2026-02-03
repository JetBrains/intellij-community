public @interface Example {

  public static final String FOO = "foo", BAR = "bar";

  String value();
}

@Example(Exam<caret>)
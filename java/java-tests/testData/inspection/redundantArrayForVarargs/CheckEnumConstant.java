
public enum CheckEnumConstant {
  A(<warning descr="Redundant array creation for calling varargs method">new String[]</warning>{"1", "2"});

  CheckEnumConstant(String... ss) {
  }
}

public enum CheckEnumConstant {
  A(<warning descr="Redundant array creation for calling varargs method">new String[]{"1", "2"}</warning>);

  CheckEnumConstant(String... ss) {
  }
}
public class AnnotatedEnumConstructor {
  private enum MyEnum {
    VAL("+", "-");
    MyEnum(String s1, @Deprecated String s2) { }
  }
}
class Foo {
  {
    Foo m = <caret>x;
  }


  static <TX extends Enum<TX>> TX valueOf(Class<TX> enumType, String name);
}
enum MyEnum {
  FOO, BAR;
}

public class Foo {

   MyEnum bar() {}

   MyEnum foo() {
        MyEnum e;
        return <caret>
    }
}
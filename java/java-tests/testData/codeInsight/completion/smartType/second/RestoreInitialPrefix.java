class MyEnum {
    public static final MyEnum Foo;
    public static final MyEnum Bar;
}

class Bar {
  MyEnum getEnum();
}

class Foo {

   Bar my;

    {
        MyEnum cl = <caret>
    }

}

enum MyEnum {
  FOO, BAR;
}

public @interface Foo {
    
    MyEnum en() default MyEnum.FOO;<caret>
}

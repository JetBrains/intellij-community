enum MyEnum {
  FOO, BAR;
}

public @interface Foo {
    
    MyEnum en() default F<caret>
}

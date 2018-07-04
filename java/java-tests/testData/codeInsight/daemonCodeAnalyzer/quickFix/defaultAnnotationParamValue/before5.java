// "Remove redundant parameter" "false"

@interface Anno {
  Class foo() default void.class;
}

@Anno(foo = Vo<caret>id.class)
class Foo {

}

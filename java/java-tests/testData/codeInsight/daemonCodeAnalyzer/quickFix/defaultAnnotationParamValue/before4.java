// "Remove redundant parameter" "true"

@interface Anno {
  Class foo() default void.class;
}

@Anno(foo = vo<caret>id.class)
class Foo {

}

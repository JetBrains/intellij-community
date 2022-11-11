// "Remove redundant parameter" "true-preview"

@interface Anno {
  Class foo() default void.class;
}

@Anno(foo = vo<caret>id.class)
class Foo {

}

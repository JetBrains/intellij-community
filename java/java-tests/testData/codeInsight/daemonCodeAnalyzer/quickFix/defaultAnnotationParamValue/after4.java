// "Remove redundant parameter" "true-preview"

@interface Anno {
  Class foo() default void.class;
}

@Anno()
class Foo {

}

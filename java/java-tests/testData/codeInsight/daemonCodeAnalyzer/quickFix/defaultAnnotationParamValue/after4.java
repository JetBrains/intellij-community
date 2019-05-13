// "Remove redundant parameter" "true"

@interface Anno {
  Class foo() default void.class;
}

@Anno()
class Foo {

}

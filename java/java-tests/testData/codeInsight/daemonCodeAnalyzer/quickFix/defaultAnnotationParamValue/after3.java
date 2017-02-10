// "Remove redundant parameter" "true"

@interface Anno {
  String foo() default "hello world";
}

@Anno()
class Foo {

}

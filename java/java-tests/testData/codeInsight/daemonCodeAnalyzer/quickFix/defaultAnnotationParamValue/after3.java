// "Remove redundant parameter" "true-preview"

@interface Anno {
  String foo() default "hello world";
}

@Anno()
class Foo {

}

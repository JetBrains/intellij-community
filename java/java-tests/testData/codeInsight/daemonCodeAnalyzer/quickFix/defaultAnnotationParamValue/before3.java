// "Remove redundant parameter" "true-preview"

@interface Anno {
  String foo() default "hello world";
}

@Anno(foo = "hello " +<caret>
            "world")
class Foo {

}

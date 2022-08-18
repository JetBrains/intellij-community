// "Remove redundant parameter" "true-preview"

@interface Anno {
    String[] foo() default {"One", "Two"};
}

@Anno(foo = {"One", <caret>"Two"})
class Foo {

}

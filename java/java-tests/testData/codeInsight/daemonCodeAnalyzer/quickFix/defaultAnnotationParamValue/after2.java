// "Remove redundant parameter" "true-preview"

@interface Anno {
    String[] foo() default {"One", "Two"};
}

@Anno()
class Foo {

}

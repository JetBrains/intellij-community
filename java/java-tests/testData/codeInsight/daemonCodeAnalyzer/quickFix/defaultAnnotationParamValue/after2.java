// "Remove redundant parameter" "true"

@interface Anno {
    String[] foo() default {"One", "Two"};
}

@Anno()
class Foo {

}

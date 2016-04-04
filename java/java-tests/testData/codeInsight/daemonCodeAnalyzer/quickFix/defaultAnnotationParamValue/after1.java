// "Remove redundant parameter" "true"

@interface Anno {
    boolean foo() default true;
}

@Anno()
class Foo {

}

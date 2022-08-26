// "Remove redundant parameter" "true-preview"

@interface Anno {
    boolean foo() default true;
}

@Anno()
class Foo {

}

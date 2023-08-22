// "Remove redundant parameter" "true-preview"

@interface Anno {
    boolean foo() default true;
}

@Anno(foo = tr<caret>ue)
class Foo {

}

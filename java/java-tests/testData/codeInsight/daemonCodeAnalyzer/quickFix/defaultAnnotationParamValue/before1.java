// "Remove redundant parameter" "true"

@interface Anno {
    boolean foo() default true;
}

@Anno(foo = tr<caret>ue)
class Foo {

}

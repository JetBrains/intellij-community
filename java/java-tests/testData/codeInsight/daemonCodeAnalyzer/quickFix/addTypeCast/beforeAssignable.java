// "Cast expression to 'java.lang.Noolean'" "false"
class X {
    boolean foo() {return true;}
    static void bar() {
        if (<caret>foo("")) {}
    }
}
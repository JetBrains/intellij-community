// "Create method 'bar'" "false"
class Test {
    {
        ba<caret>r(foo());
    }
    
    void foo() {}
}
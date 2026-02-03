class Super {
    void foo() {
        int var<caret>Name = 0;
        varName++;
        varName = bar();
    }
    int bar() {return 0;}

}

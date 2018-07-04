class Test {
    void foo() {
        //c1
        @Deprecated String f<caret>o =
           //c2                     
          ""//c3
        ;
    }
}
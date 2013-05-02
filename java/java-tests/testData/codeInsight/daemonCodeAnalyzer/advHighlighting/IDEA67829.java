class A{
    void foo(String s){}
    static void foo(Object s){<error descr="Non-static method 'foo(java.lang.String)' cannot be referenced from a static context">foo</error>("");}
}
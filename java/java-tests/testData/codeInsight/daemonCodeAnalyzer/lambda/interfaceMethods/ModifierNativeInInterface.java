interface A {
    default <error descr="Modifier 'native' not allowed here">native</error> void m(){}
    static <error descr="Modifier 'native' not allowed here">native</error> void m1(){}
    <error descr="Modifier 'native' not allowed here">native</error> void m2();
}
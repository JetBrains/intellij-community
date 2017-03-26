interface A {
    private void m() {}
}

interface B {
    <error descr="Private methods in interfaces should have a body">private void m()</error>;
}

interface C {
    private <error descr="Illegal combination of modifiers: 'default' and 'private'">default</error> void m() {}
}

interface D {
    private static void m() {}
}

interface E {
    <error descr="Illegal combination of modifiers: 'private' and 'public'">private</error> class E1 {}
}

interface F {
    <error descr="Modifier 'private' not allowed here">private</error> int m = 0;
}
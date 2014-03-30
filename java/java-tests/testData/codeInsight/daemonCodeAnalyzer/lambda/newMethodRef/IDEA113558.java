class Tmp
{
    void foo(){}

    void foo(Object... <warning descr="Parameter 'args' is never used">args</warning>){}

    Runnable r = this::foo;
}

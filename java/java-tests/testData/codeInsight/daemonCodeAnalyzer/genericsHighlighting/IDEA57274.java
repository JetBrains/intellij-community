interface I{
    void foo();
}

abstract class A {
    abstract int foo();
    abstract <<error descr="'foo()' in 'A' clashes with 'foo()' in 'I'; attempting to use incompatible return type"></error><error descr="'foo()' in 'A' clashes with 'foo()' in 'I'; attempting to use incompatible return type"></error>T extends A & I> void bar(T x);
}
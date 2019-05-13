
abstract class Bug {
    void m1(){
        D<?> jobHandler = m();
    }

    abstract <J extends C<? extends B>> J m();
}

interface B {
}

abstract class C<T extends B> {

}

abstract class D<T extends E> extends C<T> {

}

abstract class E<T extends String> implements B { }
abstract class A {
    abstract <S, T extends Iterable<S>> void foo();

    {
        foo();
    }
}

class X{
    <T extends Enum<T>> void foo(){
        foo();
    }
}
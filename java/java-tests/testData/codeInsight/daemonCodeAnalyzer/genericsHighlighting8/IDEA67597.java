class A<T>
{
    T x = <error descr="Inconvertible types; cannot cast 'int' to 'T'">(T) 1</error>;
}

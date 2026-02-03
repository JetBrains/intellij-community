abstract class  IX<T>  {
   abstract <S extends T> void foo(){}
}

class XXC<S> extends IX<Throwable> {
    <caret>
}

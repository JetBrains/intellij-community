public class Test<T> {
}

class B extends Test<Throwable>{
    <S extends Throwable> void foo(){}
}
public class Test<T> {
  <S extends T> void f<caret>oo(){}
}

class B extends Test<Throwable>{
}
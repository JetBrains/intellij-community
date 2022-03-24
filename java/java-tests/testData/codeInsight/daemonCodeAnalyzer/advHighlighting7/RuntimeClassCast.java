class Test {
  <S extends Runnable> S f() {
    return null;
  }

  {
    String m  = <warning descr="Intersection type 'Runnable & String' cannot be instantiated, because 'java.lang.String' is final">f</warning>();
    System.out.println(m);
  }
}
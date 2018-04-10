class C {
  static class MyThrowable<T> extends <error descr="Generic class may not extend 'java.lang.Throwable'">Throwable</error> { }
  void test() throws <error descr="Generic class may not extend 'java.lang.Throwable'">MyThrowable<Integer></error> { }

  private class GenericOuter<T> {
    public final Exception exn = new <error descr="Generic class may not extend 'java.lang.Throwable'">Exception</error>() { };

    {
      class LocalExn extends <error descr="Generic class may not extend 'java.lang.Throwable'">Exception</error> {}
      throw new <error descr="Generic class may not extend 'java.lang.Throwable'">RuntimeException</error>(){};
    }
  }

  public <X> void genericMethod() {
    class LocalExn extends Exception {}
    throw new RuntimeException() {};
  }

  {
    class LocalGenerics<K>  {
      class Ex extends <error descr="Generic class may not extend 'java.lang.Throwable'">Exception</error> {}
    }
  }
}
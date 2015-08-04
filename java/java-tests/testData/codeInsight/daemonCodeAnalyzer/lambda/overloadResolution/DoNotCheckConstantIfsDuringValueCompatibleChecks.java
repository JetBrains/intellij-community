class Test {
  class Message<T> {
    public T data;
  }

  class Result<<warning descr="Type parameter 'T' is never used">T</warning>> {
    public boolean isSuccessful() {
      return false;
    }
  }
  
  interface handlerReturn<R, T> {
    R process(T m);
  }
  private <R, T> void <warning descr="Private method 'process(Test.handlerReturn<R,T>)' is never used">process</warning>(handlerReturn<R, T> <warning descr="Parameter 'h' is never used">h</warning>) {}

  interface handler<T> {
    void process(T m);
  }
  private <T> void process(handler<T> <warning descr="Parameter 'h' is never used">h</warning>) {}


  public static void main(String[] args) {
    Test t = new Test();
    t.<Message<Result<String>>>process(m -> {
      if (m.data.isSuccessful());
    });
  }
}
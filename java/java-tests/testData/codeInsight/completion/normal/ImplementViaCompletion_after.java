interface Foo<T> {
  void run(T t, int myInt);
}

public class A implements Foo<String> {
    @Override
    public void run(String s, int myInt) {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}

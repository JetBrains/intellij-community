import java.util.function.BiConsumer;

class MyTest<T> {

  {
    BiConsumer<Builder<T>, T> builderTBiConsumer  = Builder::add;
    BiConsumer<Builder<T>, T> builderTBiConsumer1 = Builder<T>::add;

    System.out.println(builderTBiConsumer);
    System.out.println(builderTBiConsumer1);
  }

  public static class Builder<E> {

    public Builder<E> add(E element) {
      return this;
    }

    public Builder<E> add(E... <warning descr="Parameter 'elements' is never used">elements</warning>) {
      return this;
    }
  }
}
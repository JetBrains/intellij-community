public abstract class Foo<T extends Foo<T>> {
  private int field;

  public int bar(T t){
    return ((Foo<T>)t).field;
  }
}
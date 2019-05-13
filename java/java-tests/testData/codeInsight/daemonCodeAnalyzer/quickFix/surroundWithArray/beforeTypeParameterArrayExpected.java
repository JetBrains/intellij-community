// "Surround with array initialization" "false"
class A<T> {

  public T[] test(T t) {
    return <caret>t;
  }
}
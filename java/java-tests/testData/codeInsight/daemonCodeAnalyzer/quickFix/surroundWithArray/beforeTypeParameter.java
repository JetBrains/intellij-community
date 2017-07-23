// "Surround with array initialization" "true"
class A<T> {

  public Object[] test(T t) {
    return <caret>t;
  }
}
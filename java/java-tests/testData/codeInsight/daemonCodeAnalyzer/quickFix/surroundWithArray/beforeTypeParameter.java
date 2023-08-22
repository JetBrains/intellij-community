// "Surround with array initialization" "true-preview"
class A<T> {

  public Object[] test(T t) {
    return <caret>t;
  }
}
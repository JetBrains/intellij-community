// "Unimplement Interface" "true"
class A implements II<S<caret>tring> {
  public String toString() {
    return super.toString();
  }

    public void foo(String ty){}
}

interface II<T> {
  void foo(T ty);
}
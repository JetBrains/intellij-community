// "Unimplement" "true-preview"
class A implements A<caret> {
  public String toString() {
    return super.toString();
  }

    public void foo(String ty){}
}
// "Unimplement" "true-preview"
class A implements II<caret> {
  public String toString() {
    return super.toString();
  }

    public void foo(String ty){}
}

interface II {
}
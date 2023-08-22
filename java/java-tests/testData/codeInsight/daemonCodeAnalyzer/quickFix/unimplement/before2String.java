// "Unimplement" "true-preview"
class A implements I<caret>I {
  public String toString() {
    return super.toString();
  }
}

interface II {}
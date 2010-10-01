// "Unimplement Interface" "true"
class A implements I<caret>I {
  public String toString() {
    return super.toString();
  }
}

interface II {}
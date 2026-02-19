// "Ignore static fields with type 'java.lang.String'" "true"

class X {
  private static String nice;

  synchronized void x() {
    System.out.println(<caret>nice);
  }
}
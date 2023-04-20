// "Encapsulate field" "true-preview"

class X {
    public int x = 0;

    public static int foo() {
      X x = new X();
      return x.<caret>x;
    }
}
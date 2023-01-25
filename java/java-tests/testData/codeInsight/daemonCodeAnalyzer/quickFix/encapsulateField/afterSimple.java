// "Encapsulate field" "true-preview"

class X {
    private int x = 0;

    public static int foo() {
      X x = new X();
      return x.getX();
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }
}
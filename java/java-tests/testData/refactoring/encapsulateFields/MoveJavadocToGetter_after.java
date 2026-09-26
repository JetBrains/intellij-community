public class A {
  public int i;


  public static void main(String[] args) {
    A a = new B();
    a.setI(23);
    System.out.println(a.getI());
  }

    /**
     * my javadoc for field
     */
    public int getI() {
        return i;
    }

    public void setI(int i) {
        this.i = i;
    }
}

record A(int i) {
  public static void main(String[] args) { 
    System.out.println(new A(1));
  }
}
record R() {}
public record MyRec(String name) {
    public MyRec {
       System.out.println("Constructing");
    }

    public void doSomething() {
       System.out.println("Name is " + name);
    }

    public static void main(String[] args) {
       final MyRec rec = new MyRec("Hello");
       rec.doSomething();
    }
}
record R1(int i) {
  public static void main(String[] args){
    new R1(1);
  }
}
record R2(int i) {
  R2 {}

  public static void main(String[] args){
    new R2(0);
  }
}
record R3(int i) {
  R3(int i) {
    this.i = i;
  }

  public static void main(String... args){
    new R3(-1);
  }
}

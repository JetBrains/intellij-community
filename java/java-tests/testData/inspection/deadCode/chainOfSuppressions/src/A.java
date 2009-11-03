public class A {
    public static void main(String[] args) {
      new A();
    }

    private void a(){
    }

    private void b(){
      a();
    }

    @java.lang.SuppressWarnings({"UnusedDeclaration"})
    private void c(){
      b();
    }
}


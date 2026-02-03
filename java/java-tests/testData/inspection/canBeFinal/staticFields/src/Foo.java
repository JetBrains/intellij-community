public final class Foo {
    private static Object f1 = new Object(); // Can be final but unused
    private static Object object; // can't be final
  
    Foo() {
        object = new Object();
    }
  
    public static void main(String[] args) {
        System.out.println(Foo.f1);
        System.out.println(Foo.object);
    }
}
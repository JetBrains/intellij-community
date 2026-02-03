public class Extracted {
    public Extracted() {
    }

    public <T> void foo(T p) {
        System.out.println(p.getClass().getName());
    }
}
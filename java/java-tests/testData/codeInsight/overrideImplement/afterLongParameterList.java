public interface A {
    void foo(String p1, String p2,String p3,String p4,String p5,String p6,String p7);
}

abstract class B implements A{
    public void foo(String p1,
                    String p2,
                    String p3,
                    String p4,
                    String p5,
                    String p6,
                    String p7) {
        <caret>
    }
}
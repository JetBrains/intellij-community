public class TestIntelliJ
{

    public synchronized void foo() {
        System.out.println("foo");
    }

    public static synchronized void foo<caret>Static() {
        System.out.println("fooStatic");
    }

    public static void main(String[] args) {
        TestIntelliJ test = new TestIntelliJ();
        test.foo();
        TestIntelliJ.fooStatic();
    }
}
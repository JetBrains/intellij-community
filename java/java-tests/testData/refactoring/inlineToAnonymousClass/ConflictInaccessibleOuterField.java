public class QualifyInnerTest {
    public class C2User {
        public void test() {
            C2.C2Inner inner = new C2.C2Inner();
        }
    }
}

class C2 {
    private static int a;

    public static class <caret>C2Inner {
        public void doStuff() {
            System.out.println(a);
        }
    }
}

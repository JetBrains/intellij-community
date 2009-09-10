public class QualifiedNewTest {
    public class C2 {
        private int a;

        public class <caret>C2Inner {
            public void doStuff() {
                System.out.println(a);
            }
        }
    }

    public class C2User {
        public void test() {
            C2 c2 = new C2();
            C2.C2Inner inner = c2.new C2Inner();
        }
    }
}

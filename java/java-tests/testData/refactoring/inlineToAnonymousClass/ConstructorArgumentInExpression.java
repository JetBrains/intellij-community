public class A {
    public class <caret>Inner {
        private int a = 0;

        public Inner(int arg) {
            int j = 5;
            while (j < 10) j++;
            a = arg * j;
        }
    }

    public void test() {
        Inner i = new Inner(1);
    }
}

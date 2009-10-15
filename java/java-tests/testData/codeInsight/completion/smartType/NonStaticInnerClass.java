public class Outer {
    public class Inner {
        public Inner() {
        }
    }
}

class OuterMain {
    public static void main(String[] args) {
        Outer.Inner inner = new <caret>
    }
}
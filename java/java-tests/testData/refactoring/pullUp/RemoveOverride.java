public class Test {
    class Impl extends Base {
        @Override
        public String get() {
            return "239";
        }
    }

    abstract class Base implements Int {
        public abstract String <caret>get();

    }

    interface Int {
    }
}
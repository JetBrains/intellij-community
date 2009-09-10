public class Test {
    class Impl extends Base {
        public String get() {
            return "239";
        }
    }

    abstract class Base implements Int {

    }

    interface Int {
        String get();
    }
}
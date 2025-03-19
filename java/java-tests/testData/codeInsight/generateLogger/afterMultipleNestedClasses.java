import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Outer {
    class Inner {
        private static final Logger log<caret> = LoggerFactory.getLogger(Inner.class);

        void foo() {
        }
        class InnerMost {
        }
    }
}
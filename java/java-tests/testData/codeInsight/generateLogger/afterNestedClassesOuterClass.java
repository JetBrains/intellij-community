import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Outer {
    private static final Logger log<caret> = LoggerFactory.getLogger(Outer.class);

    class Inner {
    }
}
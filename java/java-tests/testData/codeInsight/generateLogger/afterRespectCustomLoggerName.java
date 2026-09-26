import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class A {
    private static final Logger CustomName<caret> = LoggerFactory.getLogger(A.class);
}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class A {

    private static final Logger log<caret> = LogManager.getLogger(A.class);
}
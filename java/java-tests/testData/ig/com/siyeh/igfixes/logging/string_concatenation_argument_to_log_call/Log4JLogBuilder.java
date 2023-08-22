import org.apache.logging.log4j.*;

class Log4JLogBuilder {

    void foo() {
        Logger logger = LogManager.getLogger(Log4JLogBuilder.class);
        final String CONST = "const";
        String var = "var";
        logger.atInfo().withLocation().lo<caret>g("string " + (var) + CONST);
    }
}
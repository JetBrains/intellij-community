import org.apache.logging.log4j.*;

class Log4JFormatted {

  private static final Logger logger = LogManager.getFormatterLogger(Log4JFormatted.class);

  public static void m(String a) {
    logger.i<caret>nfo("12" + a);
  }
}
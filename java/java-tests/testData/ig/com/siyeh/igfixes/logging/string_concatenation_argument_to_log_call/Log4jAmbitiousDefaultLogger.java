import org.apache.logging.log4j.*;

class Log4jAmbitiousDefaultLogger {

  public static void m(String a) {
    getLogger().i<caret>nfo("12" + a);
  }

  public native Logger getLogger();
}
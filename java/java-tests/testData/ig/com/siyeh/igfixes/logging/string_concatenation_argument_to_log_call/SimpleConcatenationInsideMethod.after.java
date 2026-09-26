import org.apache.logging.log4j.*;

class SimpleConcatenationInsideMethod {

  public void m(String a) {
      getLogger().i<caret>nfo("12{}", a);
  }

  private Logger getLogger() {
    return LogManager.getLogger(SimpleConcatenationInsideMethod.class);
  }
}
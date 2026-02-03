// "Move assignment to field declaration" "INFORMATION"
public class Test {
  static LoggingSystem LOG;

  static {
    LoggingSystem.init();
    <caret>LOG = LoggingSystem.getLogger();
  }
}

class LoggingSystem {
  private static LoggingSystem L;

  static LoggingSystem getLogger() {
    return L;
  }

  static void init() {
    if(L == null) {
      L = new LoggingSystem();
    }
  }
}


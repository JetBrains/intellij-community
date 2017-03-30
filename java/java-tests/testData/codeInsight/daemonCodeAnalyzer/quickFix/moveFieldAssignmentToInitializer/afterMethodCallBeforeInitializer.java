// "Move assignment to field declaration" "INFORMATION"
public class Test {
  static LoggingSystem LOG = LoggingSystem.getLogger();

  static {
    LoggingSystem.init();
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


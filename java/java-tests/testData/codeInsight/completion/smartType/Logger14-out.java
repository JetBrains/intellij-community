class Test {
  static class Logger {
    static Logger getLogger(Class clazz) {
      return new Logger();
    }
  }

  static final Logger logger = Logger.getLogger(Object.class)

}
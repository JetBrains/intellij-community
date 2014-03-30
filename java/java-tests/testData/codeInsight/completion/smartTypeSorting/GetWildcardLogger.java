public class Foo {
  public static final Logger LOG = Logger.getInstance(<caret>)
}

class Logger {
  static Logger getInstance(Class<?> c) {}
}
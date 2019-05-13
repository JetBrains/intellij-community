public class Foo {
  public static final Logger LOG = LoggerFactory.getLogger(<caret>)
}

class LoggerFactory {
  static Logger getLogger(Class<?> c) {}
}
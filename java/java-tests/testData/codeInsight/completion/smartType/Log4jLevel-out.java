import org.apache.log4j.Level;

class Foo {
  {
    org.apache.log4j.Category logger;
    logger.log(Level.FATAL, <caret>)
  }
}
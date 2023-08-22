import org.apache.logging.log4j.*;

class Logging {
  private static final Logger LOGGER = LogManager.getLogger(<warning descr="Logger initialized with foreign class 'String.class'">St<caret>ring.class</warning>);
  private static final Logger LOGGER2 = LogManager.getLogger(<warning descr="Logger initialized with foreign class 'String.class'">String.class</warning>.getName());
}
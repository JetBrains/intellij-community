import org.apache.logging.log4j.*;

class Logging {
  private static final Logger LOGGER = LogManager.getLogger(Logging.class);
  private static final Logger LOGGER2 = LogManager.getLogger(Logging.class.getName());
}
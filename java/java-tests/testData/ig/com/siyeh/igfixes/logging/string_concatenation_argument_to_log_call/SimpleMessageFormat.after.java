import org.slf4j.*;
import java.text.MessageFormat;

class SimpleMessageFormat {

  Logger LOG = LoggerFactory.getLogger(SimpleMessageFormat.class);

  void f() {
      LOG.info("{}, 2 {} {}", 3.0, 2, "1");
  }

}
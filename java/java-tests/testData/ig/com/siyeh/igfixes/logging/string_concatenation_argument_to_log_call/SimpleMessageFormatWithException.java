import org.slf4j.*;
import java.text.MessageFormat;

class SimpleMessageFormat {

  Logger LOG = LoggerFactory.getLogger(SimpleMessageFormat.class);

  void f() {
      LOG.in<caret>fo(MessageFormat.format("{2}, 2 {1} {0}", "1", 2, 3.0), new Exception());
  }

}
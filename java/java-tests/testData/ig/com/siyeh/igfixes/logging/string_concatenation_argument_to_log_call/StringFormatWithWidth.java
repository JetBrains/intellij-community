import org.slf4j.*;
import java.text.MessageFormat;

class StringFormatWithWidth {

  Logger LOG = LoggerFactory.getLogger(SimpleMessageFormat.class);

  void f() {
      LOG.in<caret>fo(String.format("%4$2s %3$2s %2$2s %1$2s", "a", "b", "c", "d"));
  }

}
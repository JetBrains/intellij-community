import org.slf4j.*;
import java.text.MessageFormat;

class MessageFormatMissingParameter {

  Logger LOG = LoggerFactory.getLogger(MessageFormatMissingParameter.class);

  void f() {
      LOG.in<caret>fo(MessageFormat.format("{2}, 2 {1} {0} {126}", "1", 2, 3.0));
  }

}
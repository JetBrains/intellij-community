import org.slf4j.*;
import java.text.MessageFormat;

class MessageFormatFormatter {

  Logger LOG = LoggerFactory.getLogger(MessageFormatFormatter.class);

  void f() {
      LOG.in<caret>fo(MessageFormat.format("{1}, {0, number, #.00}", 1, 2));
  }

}
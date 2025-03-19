import org.slf4j.*;
import java.text.MessageFormat;

class NumberedStringFormat {

  Logger LOG = LoggerFactory.getLogger(NumberedStringFormat.class);

  void f() {
      LOG.in<caret>fo("{} {} {} {}", "d", "c", "b", "a");
  }

}
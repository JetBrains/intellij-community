import org.slf4j.*;
import java.text.MessageFormat;

class NumberedStringFormat {

  Logger LOG = LoggerFactory.getLogger(NumberedStringFormat.class);

  void f() {
      LOG.in<caret>fo(String.format("%4$s %3$s %2$s %1$s", "a", "b", "c", "d"));
  }

}
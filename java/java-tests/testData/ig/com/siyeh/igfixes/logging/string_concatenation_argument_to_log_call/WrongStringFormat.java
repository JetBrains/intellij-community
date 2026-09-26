import org.slf4j.*;
import java.text.MessageFormat;

class WrongStringFormat {

  Logger LOG = LoggerFactory.getLogger(WrongStringFormat.class);

  void f() {
      LOG.in<caret>fo(String.format("%d something %D %d", "text", true, 1));
  }

}
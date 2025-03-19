import org.slf4j.*;
import java.text.MessageFormat;

class ConcatenationStringFormat {

  Logger LOG = LoggerFactory.getLogger(ConcatenationStringFormat.class);

  void f() {
      LOG.in<caret>fo("{} something" + " {} {}", "text", true, 1);
  }

}
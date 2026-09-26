import org.slf4j.*;
import java.text.MessageFormat;

class LessArgumentsStringFormat {

  Logger LOG = LoggerFactory.getLogger(LessArgumentsStringFormat.class);

  void f() {
      LOG.in<caret>fo(String.format("%s something %b %d", "text", true));
  }

}
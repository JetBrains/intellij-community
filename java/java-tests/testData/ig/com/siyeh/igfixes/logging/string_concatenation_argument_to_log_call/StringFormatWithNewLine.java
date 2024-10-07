import org.slf4j.*;
import java.text.MessageFormat;

class SimpleStringFormat {

  Logger LOG = LoggerFactory.getLogger(SimpleStringFormat.class);

  void f() {
    LOG.in<caret>fo(String.format("foo %n%s", "bar"));
    LOG.info(String.format("foo %%n%s", "bar"));
    LOG.info(String.format("foo %%%n%s", "bar"));
  }

}
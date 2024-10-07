import org.slf4j.*;
import java.text.MessageFormat;

class SimpleStringFormat {

  Logger LOG = LoggerFactory.getLogger(SimpleStringFormat.class);

  void f() {
    LOG.in<caret>fo("foo \n{}", "bar");
    LOG.info("foo %%n{}", "bar");
    LOG.info("foo %%\n{}", "bar");
  }

}
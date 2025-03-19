import org.slf4j.*;
import java.text.MessageFormat;

class ConcatenationMessageFormat {

  Logger LOG = LoggerFactory.getLogger(ConcatenationMessageFormat.class);

  void f() {
    LOG.in<caret>fo("{}, 2 " +
            """
                    {} {}""", 3.0, 2, "1");
  }
}
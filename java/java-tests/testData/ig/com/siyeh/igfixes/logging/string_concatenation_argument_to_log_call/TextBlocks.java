import org.slf4j.*;

class TextBlock {

  void foo() {
    Logger log = LoggerFactory.getLogger("name");
    String a = "test1";

    log.info<caret>("""
                \"\"\"
                """ + 1 + "1" + a);

    log.info("""

                """ + 1 + "1" + a);

    log.info("""
                l\s
                """ + 1 + "1" + a);

    log.info(
      """
      5        """ +
      """
      l\s
      """ + 1 + "1" + a);

    log.info(
      """
      5\n        """ +
      """
      l
      """ + " " + 1 + "1" + a);

    log.info(
      """
      5\n        """ + "\"" + "\"" + "\"" + 1 + "1" + a);

    log.info(
      """
      test
      """ + a );

    log.info(
      """
      test
      """ + a + "1");

    log.info(
      """
      test
      """ + a + """
                        a
                        """);

    log.info(1 + """
                test
                """ + a);

    log.info(1 + "test1" + """
                test2
                """ + a);

    log.info(1 + "test1\n" + """
                test2
                """ + a);

    log.info(1 + "test1\"\"\"" + """
                test2
                """ + a);

    log.info(1 + "test1" + a + """
                test2
                """);

    log.info(1 + """
           test1
                """ + """
                test2
                """ + a);

    log.info(1 +
              """
              test1        
              """ + a +
              """
          test2
              """);

  }
}
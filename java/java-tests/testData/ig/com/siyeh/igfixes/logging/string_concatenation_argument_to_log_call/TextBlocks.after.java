import org.slf4j.*;

class TextBlock {

  void foo() {
    Logger log = LoggerFactory.getLogger("name");
    String a = "test1";

      log.info("""
              ""\"
              """ + 1 + "1{}", a);

      log.info("""
                              
              """ + 1 + "1{}", a);

      log.info("""
              l\s
              """ + 1 + "1{}", a);

      log.info("""
              5l\s
              """ + 1 + "1{}", a);

      log.info("""
              5
              l
              \s""" + 1 + "1{}", a);

      log.info("""
              5
              ""\"""" + 1 + "1{}", a);

      log.info("""
              test
              {}""", a);

      log.info("""
              test
              {}1""", a);

      log.info("""
              test
              {}a
              """, a);

      log.info(1 + """
              test
              {}""", a);

      log.info(1 + """
              test1test2
              {}""", a);

      log.info(1 + """
              test1
              test2
              {}""", a);

      log.info(1 + """
              test1""\"test2
              {}""", a);

      log.info(1 + """
              test1{}test2
              """, a);

      log.info(1 + """
              test1
              test2
              {}""", a);

      log.info(1 + """
              test1
              {}test2
              """, a);

  }
}
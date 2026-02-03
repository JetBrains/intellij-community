import java.util.*;

class Test {
  void foo(){
    Properties properties = System.getProperties();
    final Map<String, String> systemProperties = <error descr="Inconvertible types; cannot cast 'java.util.Properties' to 'java.util.Map<java.lang.String,java.lang.String>'">(Map<String, String>) properties</error>;
  }
}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A extends Int {
  void foo() {
    lo<caret>
  }
}

interface Int {
  final Logger log = LoggerFactory.getLogger(Int.class);
}
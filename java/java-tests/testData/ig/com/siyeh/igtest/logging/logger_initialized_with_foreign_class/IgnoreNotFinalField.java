import java.util.logging.*;

class WarnOnlyFinalFieldAssignment {
  final Logger LOG = Logger.getLogger(<warning descr="Logger initialized with foreign class 'AnotherClass.class'">AnotherClass.class</warning>.getName());
  Logger LOG1 = Logger.getLogger(AnotherClass.class.getName());

  public void test() {
    Logger LOG = Logger.getLogger(AnotherClass.class.getName());
  }
}
class AnotherClass {

}
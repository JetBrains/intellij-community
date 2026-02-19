import java.util.logging.*;

public class IgnoreNonPublicClasses {
  Logger LOG = Logger.getLogger(<warning descr="Logger initialized with foreign class 'NonPublicClass.class'">NonPublicClass.class</warning>.getName());

}
class NonPublicClass {
  Logger LOG = Logger.getLogger(IgnoreNonPublicClasses.class.getName()); // ignored

}
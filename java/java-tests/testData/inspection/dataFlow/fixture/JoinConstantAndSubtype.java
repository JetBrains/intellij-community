import org.jetbrains.annotations.*;
import java.util.List;

abstract class Super {
  public static final Sub SUB = new Sub();
}

class Sub extends Super {
}
public class JoinConstantAndSubtype {

  void check(Super s) {
    if (s != Super.SUB  && s instanceof Sub) return;
    if (s != Super.SUB) {

    }
  }
}
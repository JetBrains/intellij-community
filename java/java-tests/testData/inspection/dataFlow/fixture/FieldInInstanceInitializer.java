import org.jetbrains.annotations.*;

public class FieldInInstanceInitializer {
  Object getObject() {
    return new Object() {
      final Object f;

      {
        f = new Object();
        if (<warning descr="Condition 'f == null' is always 'false'">f == null</warning>) {}
        unknown();
        System.out.println(f.hashCode());
      }

      native void unknown();
    };
  }
}
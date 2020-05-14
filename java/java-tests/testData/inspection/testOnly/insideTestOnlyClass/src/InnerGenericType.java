import org.jetbrains.annotations.TestOnly;
import java.util.List;

@TestOnly
public class Bar {
  static abstract class Foo implements List<Bar> {
  }
}
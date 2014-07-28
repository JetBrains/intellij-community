import java.util.concurrent.atomic.AtomicBoolean;

// "Convert to atomic" "true"
class Test {
  final AtomicBoolean field= new AtomicBoolean(false);
  {
    boolean b = !field.get();
  }
}
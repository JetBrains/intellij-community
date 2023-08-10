import java.util.concurrent.atomic.AtomicReference;

// "Convert to atomic" "true"
class Test {
    static final AtomicReference<String> field = new AtomicReference<>("br");
  {
    new Test(field.get());
  }

  Test(String field) { }
}
import java.util.concurrent.atomic.AtomicReference;

// "Convert to atomic" "true"
class X {
  private final AtomicReference<String> s = new AtomicReference<>("");
    private String t;
    private String u;
}
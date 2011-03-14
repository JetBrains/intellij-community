// "Make final and annotate as @SafeVarargs" "true"
public class Test {
  @SafeVarargs
  public final <T> void m<caret>ain(T... args) {

  }
}


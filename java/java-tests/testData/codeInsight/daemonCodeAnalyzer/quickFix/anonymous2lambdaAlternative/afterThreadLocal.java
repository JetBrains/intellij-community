// "Replace anonymous class with ThreadLocal.withInitial" "true"
public class Main {
    // comment
    ThreadLocal<? extends CharSequence> tlr = ThreadLocal.withInitial(() -> "initial");
}
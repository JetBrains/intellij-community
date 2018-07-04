import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
    final AtomicInteger i = new AtomicInteger();

 int j = i.get() + 5;
 String s = "i = " + i;
}
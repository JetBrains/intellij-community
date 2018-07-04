import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {

    {
        AtomicInteger i = new AtomicInteger();
        Integer j = 0;

        assert j == i.get();
    }
}
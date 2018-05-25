
import java.util.Arrays;
import java.util.stream.Stream;

class Main {
    public static void main(String[] args) {
        var stream1 = Stream.of(Arrays.asList("1"), Arrays.asList(1));
        var stream2 = stre<caret>am1.map(list -> list);

    }
}
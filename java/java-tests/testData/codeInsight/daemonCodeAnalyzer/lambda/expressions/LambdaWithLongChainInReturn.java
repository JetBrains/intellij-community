
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
class MySample {
    <T> MySample bar(Function<String, T> f) {return null;}
    <T> MySample bar(BiFunction<String, String, T> f) {return null;}
    private void example() {
        update(x -> x.bar(y -> {
            var e1 = Optional.of(y);
            var e2 = Optional.of(e1.orElse(y + 1));
            var e3 = Optional.of(e2.orElse(y + 1));
            var e4 = Optional.of(e3.orElse(y + 1));
            var e5 = Optional.of(e4.orElse(y + 1));
            var e6 = Optional.of(e5.orElse(y + 1));
            var e7 = Optional.of(e6.orElse(y + 1));
            var e8 = Optional.of(e7.orElse(y + 1));
            var e9 = Optional.of(e8.orElse(y + 1));
            var e10 = Optional.of(e9.orElse(y + 1));
            return Arrays.as<caret>List(e1, e2);
        }));
    }
    void update(UnaryOperator<MySample> u) {
    }
}
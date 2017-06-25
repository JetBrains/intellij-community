// "Implement methods" "true"
import java.util.Optional;

interface I<T> {
    Optional<T> foo();
}
class Impl implements I<String> {
    @Override
    public Optional<String> foo() {
        return Optional.empty();
    }
}
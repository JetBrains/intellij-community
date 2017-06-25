// "Implement methods" "true"
import java.util.Optional;

interface I<T> {
    Optional<T> foo();
}
class I<caret>mpl implements I<String> {
}
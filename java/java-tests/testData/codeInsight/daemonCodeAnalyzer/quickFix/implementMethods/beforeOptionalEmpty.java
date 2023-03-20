// "Implement methods" "true-preview"
import java.util.Optional;

interface I<T> {
    Optional<T> foo();
}
class I<caret>mpl implements I<String> {
}
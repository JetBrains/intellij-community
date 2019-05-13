import java.util.List;

public class Wrapper {
    private final List<String> value;

    public Wrapper(@TA List<@TA String> value) {
        this.value = value;
    }

    public @TA List<@TA String> getValue() {
        return value;
    }
}

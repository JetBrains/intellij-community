import org.jetbrains.annotations.NotNull;

public enum EnumConstructor {
    Value("label");

    private final String label;

    EnumConstructor(@NotNull String label) {
        this.label = label;
    }
}

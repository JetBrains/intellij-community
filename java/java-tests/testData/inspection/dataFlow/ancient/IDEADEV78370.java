import org.jetbrains.annotations.Nullable;

class NoWarnings {
    int f(@Nullable String value)  {
        value = value == null ? "" : value;
        return value.hashCode();
    }
}

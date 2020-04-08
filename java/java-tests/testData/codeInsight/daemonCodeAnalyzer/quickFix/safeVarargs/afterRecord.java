// "Annotate as @SafeVarargs" "true"
public record Test(java.util.List<String>... args) {
    @SafeVarargs
    public Test {
    }
}


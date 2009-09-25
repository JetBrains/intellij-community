import org.jetbrains.annotations.Nullable;

public class Bar {

    void navigateTo() {
        Computable c = new Computable() {
            @Nullable  
            public Object compute() {
                return null;
            }
        };
    }

}

interface Computable {
    @Nullable Object compute();
}
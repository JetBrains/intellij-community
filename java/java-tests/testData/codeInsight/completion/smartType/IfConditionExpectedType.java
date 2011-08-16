interface Computable<T> {
    T compute();
}

public class Zoo2 {
    <T> T run(Computable<T> computable) {
        
    }

    {
        
        if (run(new <caret>))
    }
}

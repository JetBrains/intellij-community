interface Computable<T> {
    T compute();
}

public class Zoo2 {
    <T> T run(Computable<T> computable) {
        
    }

    {
        
        if (run(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
                <selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
            }
        }))
    }
}

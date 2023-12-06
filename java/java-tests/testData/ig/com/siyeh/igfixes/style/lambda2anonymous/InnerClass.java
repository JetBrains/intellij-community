import java.io.Serializable;

public abstract class LambdaRefInnerClass {
    public abstract class Holder { }

    public static final LongKey<Holder> func = va<caret>lue -> 0L;
}

interface LongKey<V> extends Serializable {
    Long getKey(V value);
}
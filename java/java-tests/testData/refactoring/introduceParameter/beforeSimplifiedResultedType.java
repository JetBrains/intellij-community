import java.util.*;
public class IntroduceParameterProducesWarning {
    private final List<Generic<?>> elements;

    public IntroduceParameterProducesWarning() {
        elements = <selection>newArrayList(new SomeGeneric(), new SomeOtherGeneric())</selection>;
    }

    public static <E> ArrayList<E> newArrayList(E... elements) {
        ArrayList<E> list = new ArrayList<E>();
        Collections.addAll(list, elements);
        return list;
    }

    private static interface Generic<T>{

    }

    private static class SomeGeneric implements Generic<String> {
    }

    private static class SomeOtherGeneric implements Generic<Object> {
    }
}

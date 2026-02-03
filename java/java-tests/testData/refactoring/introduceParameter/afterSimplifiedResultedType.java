import java.util.*;
public class IntroduceParameterProducesWarning {
    private final List<Generic<?>> elements;

    public IntroduceParameterProducesWarning(final ArrayList<Generic<?>> anObject) {
        elements = anObject;
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

class FindOp<T, O> {

    public static<MT> FindOp<MT, Optional<MT>> makeRef() {
        return new FindOp<>(Optional.empty(), Optional::isPresent, FindSink.OfRef::new);
    }


    static abstract class FindSink<FindSinkT, FindSinkO> implements TerminalSink<FindSinkT, FindSinkO> {
        @Override
        public void accept(FindSinkT value) {}
        static class OfRef<OfRefT> extends FindSink<OfRefT, Optional<OfRefT>> {
            @Override
            public Optional<OfRefT> getAndClearState() {
                return null;
            }
        }
    }

     private FindOp(O emptyValue,
                   Predicate<O> presentPredicate,
                   Supplier<TerminalSink<T, O>> sinkSupplier) {}
}

class FindOp1<T, O> {

    public static <MT> FindOp1<MT, Optional<MT>> makeRef() {
        return new FindOp1<>(Optional.empty(), Optional::isPresent);
    }

    public static <MT> FindOp1<MT, Optional<MT>> makeRef1() {
        return new FindOp1<>(Optional.empty(), t -> t.isPresent());
    }


     private FindOp1(O emptyValue, Predicate<O> presentPredicate) {}
}

class FindOp2<T, O> {

    public static <MT> FindOp2<MT, Optional<MT>> makeRef() {
        return new FindOp2<>(Optional.empty());
    }

    private FindOp2(O emptyValue) {}
}

final class Optional<T> {
    private Optional(T value) {
    }

    private Optional() {
    }
    @SuppressWarnings("unchecked")
    public static<T> Optional<T> empty() {
        return null;
    }

    public static <T> Optional<T> of(T value) {
        return new Optional<>(value);
    }

    public T get() {
        return null;
    }

    public boolean isPresent() {
        return true;
    }
}

interface Predicate<T> {
   public boolean test(T t);
}
interface Supplier<T> {
    public T get();
}
interface Consumer<T> {
    public void accept(T t);
}

interface IntConsumer {
    public void accept(int value);
}

interface Sink<T> extends Consumer<T> {


    default void accept(int value) {
        throw new IllegalStateException("called wrong accept method");
    }
    @FunctionalInterface
    interface OfInt extends Sink<Integer>, IntConsumer {
        @Override
        void accept(int value);

        @Override
        default void accept(Integer i) {
            accept(i.intValue());
        }
    }
}

interface TerminalSink<T, R> extends Sink<T> {
    R getAndClearState();
}
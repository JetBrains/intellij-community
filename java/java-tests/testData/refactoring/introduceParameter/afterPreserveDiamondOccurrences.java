
public class TestCompletion {

    public static <T, V> ParallelPipeline<T, V> test(T base, V newStage, T upstream, final ParallelPipeline<T, V> anObject) {
        if (base != null){
            return anObject;
        }
        else {
            return new ParallelPipeline<>(upstream, newStage);
        }

    }


    void f() {
        test(null, null, null, new ParallelPipeline<>(null, null));
    }
    private static class ParallelPipeline<T, V> {
        public ParallelPipeline(T p0, V p1) {
        }
    }
}

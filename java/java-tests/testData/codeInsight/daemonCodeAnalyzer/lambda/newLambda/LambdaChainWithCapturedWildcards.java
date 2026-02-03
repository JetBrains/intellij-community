import java.util.function.Supplier;
import java.util.function.Function;

abstract class Initial<A, B> {
    
    abstract <A1> Initial<A1, B> leftMap(Function<? super A, ? extends A1> leftTransform);

    abstract <A1, B1> Initial<A1, ?> leftFlatMap(Function<? super A, ? extends Initial<? extends A1, ? extends B1>> leftTransform);

    
    public void testThingy(final Initial<Integer, Double> left23, 
                           final Initial<Integer, Double> left42, 
                           final Initial<Integer, Double> left57) {
        final Initial<Integer, ?> r2 = left23.leftFlatMap(i -> left42.leftFlatMap(j -> left57.leftMap(k -> i + j + k)));

    }
}

//simplified
abstract class Either<A, B> {
    abstract <A1, B1> void first(Supplier<Either<A1, ? extends B1>> leftTransform);
    abstract <A2, B2> Either<A2, ?> second(Supplier<Either<A2, ? extends B2>> leftTransform);
    abstract <A3> Either<A3, B> third(Supplier<A3> leftTransform);

    public void test(A a) {
        first(() -> second(() -> third(() -> a)));
    }

}

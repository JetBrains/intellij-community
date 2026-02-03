package pack1;

public class C {
    static <A> POne<Hooray<A>> sequence(final Hooray<POne<A>> as) {
        return new POne<Hooray<A>>() {
            public Hooray<A> _1() {
                return as.map(C.<A>__1());
            }
        };
    }

    static <A> Eff<POne<A>, A> __1() {
        return POne::_1;
    }
}
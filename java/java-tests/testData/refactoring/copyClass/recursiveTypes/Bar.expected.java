public class Bar<A> {
    public Bar(Bar<A> f) {
    }

    Bar<Bar<A>> foos() {
        return new Bar<Bar<A>>(new Bar<Bar<A>>(null));
    }
} 
class Collection<T> {}

class CommentCompletion<T> {

    static {
        Collection<Integer> c;
        new CommentCompletion<Integer>(c);
    }

    <E extends T> CommentCompletion(Collection<E> collection) {

    }

}
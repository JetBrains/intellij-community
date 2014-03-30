interface Producer<T> {

  <E extends Exception> void drainTo( Consumer<? super T, E> consumer, Object someParameter ) throws E;

}

interface Consumer<T, E extends Exception> {

  void consume( T message ) throws E;

}


interface A { }

interface B<T extends A> { }

interface C<T> extends A { }

interface D<T> extends B<C<? super T>> { }

interface E<T extends A, U extends B<? super T>> { }

interface F<T> extends E<C<? super T>, <error descr="Type parameter 'D' is not within its bound; should extend 'B<? super C<? super T>>'">D<? super T></error>> { }
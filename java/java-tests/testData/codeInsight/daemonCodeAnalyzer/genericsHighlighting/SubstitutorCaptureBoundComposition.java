interface NodeTraverser<T> {
  NodeTraverser<T> filter(Predicate<? super T> filter);

  NodeTraverser<T> filter1(Predicate<T> filter);

  NodeTraverser<T> filter2(Predicate<? extends T> filter);
}

interface Predicate<P> {
}

class Test {

  void foo(NodeTraverser<?> traverser,
           NodeTraverser<? extends String> traverser1,
           NodeTraverser<? super String> traverser2,

           Predicate<?> filter,
           Predicate<? extends String> filter1,
           Predicate<? super String> filter2) {

    traverser.filter<error descr="'filter(Predicate<? super capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;
    traverser.filter1<error descr="'filter1(Predicate<capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;
    traverser.filter2<error descr="'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>; //"'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'"

    traverser1.filter<error descr="'filter(Predicate<? super capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;
    traverser1.filter1<error descr="'filter1(Predicate<capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;
    traverser1.filter2<error descr="'filter2(Predicate<? extends capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>; // "'filter2(Predicate<? extends capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'"

    traverser2.filter<error descr="'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>; // "'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'"
    traverser2.filter1<error descr="'filter1(Predicate<capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;
    traverser2.filter2<error descr="'filter2(Predicate<? extends capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<?>>)'">(filter)</error>;



    traverser.filter<error descr="'filter(Predicate<? super capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;
    traverser.filter1<error descr="'filter1(Predicate<capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;
    traverser.filter2<error descr="'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;// "'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'"

    traverser1.filter<error descr="'filter(Predicate<? super capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;
    traverser1.filter1<error descr="'filter1(Predicate<capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;
    traverser1.<error descr="Cannot resolve symbol 'filter2'">filter2</error>>(filter1);// "'filter2(Predicate<? extends capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'"

    traverser2.filter<error descr="'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;//  "'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'"
    traverser2.filter1<error descr="'filter1(Predicate<capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? extends java.lang.String>>)'">(filter1)</error>;
    traverser2.filter2(filter1);



    traverser.filter<error descr="'filter(Predicate<? super capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;
    traverser.filter1<error descr="'filter1(Predicate<capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;
    traverser.filter2<error descr="'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;//  "'filter2(Predicate<? extends capture<?>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'"

    traverser1.filter(filter2);
    traverser1.filter1<error descr="'filter1(Predicate<capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;
    traverser1.filter2<error descr="'filter2(Predicate<? extends capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;// "'filter2(Predicate<? extends capture<? extends java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'"

    traverser2.filter<error descr="'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;// "'filter(Predicate<? super capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'"
    traverser2.filter1<error descr="'filter1(Predicate<capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;
    traverser2.filter2<error descr="'filter2(Predicate<? extends capture<? super java.lang.String>>)' in 'NodeTraverser' cannot be applied to '(Predicate<capture<? super java.lang.String>>)'">(filter2)</error>;
  }

}

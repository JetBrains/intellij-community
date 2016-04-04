class TypeArgsConsistency {

    interface I<T> {
        T m(int i, int j);
    }

    static void foo(I<Integer> s) { }

    static <X> I<X> bar(I<X> s) { return null; }

    {
      I<Integer> i1 = (i, j) -> i + j;
      foo((i, j) -> i + j);
      I<Integer> i2 = bar((i, j) -> i + j);
      I<Integer> i3 = bar(<error descr="no instance(s) of type variable(s)  exist so that String conforms to Integer
inference variable X has incompatible bounds:
 equality constraints: Integer
lower bounds: String">(i, j) -> "" + i + j</error>);
    }
}

class TypeArgsConsistency1 {

    interface I<T> {
        int m(int i, T j);
    }

    static void foo(I<Integer> s) { }

    static <X> I<X> bar(I<X> s) { return null; }

    {
        I<Integer> i1 = (i, j) -> i + j;
        foo((i, j) -> i + j);
        I<Integer> i2 =bar((i, j) -> i) ;
        I<Integer> i3 = bar(<error descr="Bad return type in lambda expression: String cannot be converted to int">(i, j) -> "" + i + j</error>);
    }
}

class TypeArgsConsistency2 {
    static <T> I<T> bar(I<T> i) {return null;}
    static <T> I1<T> bar1(I1<T> i) {return null;}
    static <T> I2<T> bar2(I2<T> i) {return i;}

    public static void main(String[] args) {
        I<Integer> i1 = bar(x -> x);
        I1<Integer> i2 = bar1(x -> 1);
        I2<String> aI2 = bar2(x -> "");
        I2<Integer> aI28 = bar2( <error descr="no instance(s) of type variable(s)  exist so that String conforms to Integer
inference variable T has incompatible bounds:
 equality constraints: Integer
lower bounds: String">x-> ""</error>);
        I2<Integer> i3 = bar2(x -> x);
        I2<Integer> i4 = bar2(x -> foooI());
        System.out.println(i4.foo(2));
    }

    static <K> K fooo(){return null;}
    static int foooI(){return 0;}
   
    interface I<X> {
        X foo(X x);
    }
    interface I1<X> {

        int foo(X x);
    }

    interface I2<X> {
        X foo(int x);
    }
}

class TypeArgsConsistency3 {
    public static void main(String[] args) { 
        doIt1(1, x -> doIt1(x, y -> x * y));
        doIt1(1, x -> x);
        doIt1(1, x -> x * x);
    }
    interface F1<ResultType, P1> { ResultType _(P1 p); }
    static <T> T doIt1(T i, F1<T,T> f) { return f._(i);}
}


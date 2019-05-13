interface Func<TIn, TOut>{
    TOut run(TIn in);
}


class Main {

    public static void main(final String[] args) {
        <error descr="Incompatible types. Found: '<method reference>', required: 'Func<java.lang.Integer,java.lang.String>'">Func<Integer, String> func =  Integer::toString;</error>
        System.out.println(func.run(6));
    }
}
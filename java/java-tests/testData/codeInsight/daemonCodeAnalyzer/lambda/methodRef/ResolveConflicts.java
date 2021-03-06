interface Func<TIn, TOut>{
    TOut run(TIn in);
}


class Main {

    public static void main(final String[] args) {
        Func<Integer, String> func =  Integer::<error descr="Reference to 'toString' is ambiguous, both 'toString(int)' and 'toString()' match">toString</error>;
        System.out.println(func.run(6));
    }
}
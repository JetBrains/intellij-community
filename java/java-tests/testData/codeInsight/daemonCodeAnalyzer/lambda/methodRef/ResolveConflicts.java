interface Func<TIn, TOut>{
    TOut run(TIn in);
}


class Main {

    public static void main() {
        Func<Integer, String> func =  Integer::<error descr="Reference to 'toString' is ambiguous, both 'toString()' and 'toString(int)' match">toString</error>;
        System.out.println(func.run(6));
    }
}
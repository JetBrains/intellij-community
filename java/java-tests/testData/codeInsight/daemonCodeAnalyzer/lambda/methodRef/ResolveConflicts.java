interface Func<TIn, TOut>{
    TOut run(TIn in);
}


class Main {

    public static void main(final String[] args) {
        Func<Integer, String> func =  Integer::<error descr="Cannot resolve method 'toString'">toString</error>;
        System.out.println(func.run(6));
    }
}
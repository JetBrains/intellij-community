class Demo {
    public static void main(String[] args) {
        Runnable r = () -> <error descr="void is not a functional interface">() -> () -> {}</error>;
        <error descr="Cannot resolve symbol 'Supplier'">Supplier</error><String> unresolved = String::new;
    }
}

class Demo {
    public static void main(String[] args) {
        Runnable r = () -> <error descr="void is not a functional interface">() -> () -> {}</error>;
    }
}

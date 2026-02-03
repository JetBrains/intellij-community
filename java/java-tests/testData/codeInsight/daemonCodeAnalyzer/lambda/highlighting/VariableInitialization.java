class Test1 {
    {
        Runnable r = () -> { <error descr="Variable 'r' might not have been initialized">r</error>.run(); };
    }
}
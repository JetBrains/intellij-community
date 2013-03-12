// "Replace lambda with method reference" "true"
class Example {
    interface Jjj {
        int[] jjj(int p);
    }

    {
        Jjj jjj = int[]::new;
    }
}
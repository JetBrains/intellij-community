package pkg;

class Chars  {
    public static final char c = 'c';

    @CharAnno('c') public static char c() { return c; }
}

@interface CharAnno {
    char value();
}

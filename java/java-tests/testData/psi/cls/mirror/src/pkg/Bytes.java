package pkg;

class Bytes  {
    public static final byte b = 1;

    @ByteAnno(1) public static byte b() { return b; }
}

@interface ByteAnno {
    byte value();
}

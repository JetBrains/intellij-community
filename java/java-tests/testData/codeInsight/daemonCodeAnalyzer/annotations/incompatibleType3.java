@interface Ann {
    short[] u () default 0;
}

@Ann(u={<error descr="Incompatible types. Found: 'int', required: 'short'">2222222</error>}) class D {
}

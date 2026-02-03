@interface Ann {
    int u () default 0;
}

@Ann(u=<error descr="Incompatible types. Found: 'double', required: 'int'">0.0</error>) class D {
}

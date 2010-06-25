@interface Ann {
    int u () default 0;
}

@Ann(<error descr="Cannot resolve method 'v'">v</error>=0) class D {
}

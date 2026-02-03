@interface Ann {
    int u () default 0;
}

@Ann(<error descr="Cannot find @interface method 'v()'">v=0</error>) class D {
}

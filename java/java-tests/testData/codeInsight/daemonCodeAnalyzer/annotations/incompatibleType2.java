@interface Ann {
    int[] u () default 0;
}

@Ann(u=0) class D {
}

@interface Ann {
    int i ();
}

@<error descr="'i' missing but required">Ann</error>() class D {
}
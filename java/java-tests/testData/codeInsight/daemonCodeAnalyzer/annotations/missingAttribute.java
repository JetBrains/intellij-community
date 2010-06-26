@interface Ann {
    int i ();
}

@<error descr="'i' missing though required">Ann</error>() class D {
}
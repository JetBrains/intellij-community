module M {
    provides my.api.MyService with my.impl.MyServiceImpl;
    provides my.api.MyService with my.impl.<caret>MyServiceImpl1;
}
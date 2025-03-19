@interface Ann {
    int u () default 0;
}

@Ann(<error descr="Cannot find @interface method 'value()'">0</error>) class D {
}

@In(<error descr="Annotation attribute of the form 'name=value' expected">""</error>, create = "") 
class ZZZ {
}
@interface In {
    String value();
    String create();
}

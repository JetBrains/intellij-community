@interface Ann {
    int u () default 0;
}

@Ann(<error descr="Cannot find method 'value'">0</error>) class D {
}

@In(""<error descr="Annotation attribute must be of the form 'name=value'">,</error> create = "") 
class ZZZ {
}
@interface In {
    String value();
    String create();
}

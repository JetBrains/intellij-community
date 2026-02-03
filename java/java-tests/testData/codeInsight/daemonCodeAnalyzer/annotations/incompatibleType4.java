@interface Ann {
    Inner1 inner ();
}

@interface Inner1 {
  int i ();
}

@interface Inner2 {
  int i ();
}


@Ann(inner=<error descr="Incompatible types. Found: 'Inner2', required: 'Inner1'">@Inner2(i=0)</error>) class D {
}

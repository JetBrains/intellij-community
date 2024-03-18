@interface Anno {
  boolean attr() default true;
  int existing();
}

@Anno(att<caret>existing = 2)
class Cls {
  
}
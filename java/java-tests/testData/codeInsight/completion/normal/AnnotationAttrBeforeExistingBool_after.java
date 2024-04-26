@interface Anno {
  boolean attr() default true;
  int existing();
}

@Anno(attr = false<caret>, existing = 2)
class Cls {
  
}
@interface Anno {
  String attr();
  int existing();
}

@Anno(attr = <caret>, existing = 2)
class Cls {
  
}
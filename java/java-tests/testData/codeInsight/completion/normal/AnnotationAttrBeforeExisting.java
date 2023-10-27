@interface Anno {
  String attr();
  int existing();
}

@Anno(att<caret>existing = 2)
class Cls {
  
}
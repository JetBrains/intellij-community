// "Fix all 'Remove underscores from literal' problems in file" "true"

class C {
  int c = <caret>299_792_000;
  float pi = 3.141_592_6f;
  long avogadro = 6_022_140_000_000_000_000l * 100_000;
  double e = 2.718_281_828_459_045_235_360_287_471_352_662_497_757_247_093_699_959_574_966;
}

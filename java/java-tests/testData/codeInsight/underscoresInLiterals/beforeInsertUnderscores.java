// "Fix all 'Unreadable numeric literal' problems in file" "true"

class C {
  int c = <caret>299792000;
  float pi = 3.1415926f;
  long avogadro = 6022140000000000000l * 100000;
  double e = 2.718281828459045235360287471352662497757247093699959574966;
  int earthRadiusHex = 0x615299;
  long earthMassOctal = 0513315465723505200000L * 1000000;
  char escapeVelocityBin = 0b0010101110110010;
}

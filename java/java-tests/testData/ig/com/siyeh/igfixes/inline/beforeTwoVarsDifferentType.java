// "Inline variable|->Keep 'pd' name" "false"
package com.example;

class CastNeeded {

  double m(int p) {
    double p<caret>d = p;
    return pd/100;
  }
}

// "Inline variable" "true-preview"
package com.siyeh.igfixes.inline;

class CastNeeded {

  double m(int p) {
    double pd<caret> = p;
    return pd/100;
  }
}
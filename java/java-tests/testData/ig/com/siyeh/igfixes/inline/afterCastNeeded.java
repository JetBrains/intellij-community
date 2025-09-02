// "Inline variable" "true-preview"
package com.siyeh.igfixes.inline;

class CastNeeded {

  double m(int p) {
      return (double) p /100;
  }
}